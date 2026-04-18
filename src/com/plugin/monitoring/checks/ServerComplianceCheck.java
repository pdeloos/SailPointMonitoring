package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates server compliance:
 * 1. Required efix releases for the current SailPoint version are installed
 * 2. Java version meets minimum requirements for the installed major version
 * 3. Tomcat version meets the minimum requirement
 */
public class ServerComplianceCheck implements IMonitoringCheck {
  
  private static final Log log = LogFactory.getLog(ServerComplianceCheck.class);
  
  private String complianceJsonUrl;
  
  public ServerComplianceCheck(String complianceJsonUrl) {
    this.complianceJsonUrl = complianceJsonUrl;
  }
  
  @Override
  public String getName() {
    return "ServerCompliance";
  }
  
  @Override
  public List<MonitoringResult> execute(SailPointContext context) throws Exception {
    List<MonitoringResult> results = new ArrayList<>();
    
    try {
      // Fetch compliance JSON from web page
      String complianceJson = fetchComplianceJson();
      
      String sailpointVersion = StringUtils.substringBefore(Version.getFullVersion(), " ");
      
      // Parse the compliance data
      log.debug("SailPoint version detected: " + sailpointVersion);
      ComplianceData data = parseComplianceJson(complianceJson, sailpointVersion);
      
      // Check efixes
      MonitoringResult efixResult = checkEfixes(context, sailpointVersion, data);
      results.add(efixResult);
      
      // Check Java version
      MonitoringResult javaResult = checkJavaVersion(data);
      results.add(javaResult);
      
      // Check Tomcat version
      MonitoringResult tomcatResult = checkTomcatVersion(context, data);
      results.add(tomcatResult);
      
    } catch (Exception e) {
      log.error("ServerComplianceCheck failed", e);
      results.add(new MonitoringResult(getName(), Status.ERROR,
              "Server compliance check failed: " + e.getMessage())
              .addDetail("exception", e.getClass().getSimpleName()));
    }
    
    return results;
  }
  
  /**
   * Fetch compliance JSON from the configured URL
   */
  private String fetchComplianceJson() throws Exception {
    CloseableHttpClient httpClient = HttpClients.createDefault();
    try {
      HttpGet httpGet = new HttpGet(complianceJsonUrl);
      httpGet.setHeader("Accept", "application/json");
      
      return httpClient.execute(httpGet, response -> {
        if (response.getStatusLine().getStatusCode() == 200) {
          return EntityUtils.toString(response.getEntity(), "UTF-8");
        } else {
          try {
            throw new Exception("Failed to fetch compliance JSON: " + response.getStatusLine());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
    } finally {
      httpClient.close();
    }
  }
  
  /**
   * Parse compliance JSON and extract data for the given SailPoint version
   */
  private ComplianceData parseComplianceJson(String json, String sailpointVersion) throws Exception {
    // Simple JSON parsing - you may want to use a proper JSON library
    // For now, using basic string parsing
    
    // Extract version key (e.g., "8.5p1")
    String versionKey = extractVersionKey(json, sailpointVersion);
    
    if (versionKey == null) {
      throw new Exception("No compliance data found for SailPoint version: " + sailpointVersion);
    }
    
    ComplianceData data = new ComplianceData();
    data.sailpointVersion = sailpointVersion;
    
    // Extract efixes
    data.requiredEfixes = extractEfixes(json, versionKey);
    
    // Extract Java requirements
    data.javaRequirements = extractJavaRequirements(json, versionKey);
    
    // Extract Tomcat version
    data.requiredTomcatVersion = extractTomcatVersion(json, versionKey);
    
    return data;
  }
  
  /**
   * Find the matching version key in the JSON
   */
  private String extractVersionKey(String json, String sailpointVersion) {
    // Match version pattern like "8.5p1"
    Pattern pattern = Pattern.compile("\"(" + Pattern.quote(sailpointVersion) + ")\"\\s*:");
    Matcher matcher = pattern.matcher(json);
    if (matcher.find()) {
      return matcher.group(1);
    }
    
    // Fallback: try to match major.minor pattern
    Pattern fallbackPattern = Pattern.compile("\"(\\d+\\.\\d+p\\d+)\"\\s*:");
    matcher = fallbackPattern.matcher(json);
    if (matcher.find()) {
      return matcher.group(1);
    }
    
    return null;
  }
  
  /**
   * Extract required efixes from the JSON
   */
  private List<String> extractEfixes(String json, String versionKey) {
    List<String> efixes = new ArrayList<>();
    
    // Pattern: "efixes": ["EQN-001", "EQN-002"]
    Pattern pattern = Pattern.compile("\"efixes\"\\s*:\\s*\\[(.*?)\\]");
    Matcher matcher = pattern.matcher(json);
    
    if (matcher.find()) {
      String efixesStr = matcher.group(1);
      Pattern efixPattern = Pattern.compile("\"([A-Z]+-\\d+)\"");
      Matcher efixMatcher = efixPattern.matcher(efixesStr);
      
      while (efixMatcher.find()) {
        efixes.add(efixMatcher.group(1));
      }
    }
    
    return efixes;
  }
  
  /**
   * Extract Java version requirements
   */
  private List<JavaRequirement> extractJavaRequirements(String json, String versionKey) {
    List<JavaRequirement> requirements = new ArrayList<>();
    
    // Pattern: "java": [{"major": 11, "minimumVersion": "11.0.21"}, ...]
    Pattern pattern = Pattern.compile("\"java\"\\s*:\\s*\\[(.*?)\\]");
    Matcher matcher = pattern.matcher(json);
    
    if (matcher.find()) {
      String javaStr = matcher.group(1);
      
      // Split by } to get individual Java version objects
      String[] javaObjs = javaStr.split("}");
      
      for (String javaObj : javaObjs) {
        JavaRequirement req = new JavaRequirement();
        
        // Extract major version
        Pattern majorPattern = Pattern.compile("\"major\"\\s*:\\s*(\\d+)");
        Matcher majorMatcher = majorPattern.matcher(javaObj);
        if (majorMatcher.find()) {
          req.majorVersion = Integer.parseInt(majorMatcher.group(1));
        }
        
        // Extract minimum version
        Pattern minVersionPattern = Pattern.compile("\"minimumVersion\"\\s*:\\s*\"([^\"]+)\"");
        Matcher minVersionMatcher = minVersionPattern.matcher(javaObj);
        if (minVersionMatcher.find()) {
          req.minimumVersion = minVersionMatcher.group(1);
        }
        
        if (req.majorVersion > 0 && req.minimumVersion != null) {
          requirements.add(req);
        }
      }
    }
    
    return requirements;
  }
  
  /**
   * Extract required Tomcat version
   */
  private String extractTomcatVersion(String json, String versionKey) {
    Pattern pattern = Pattern.compile("\"tomcat\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(json);
    
    if (matcher.find()) {
      return matcher.group(1);
    }
    
    return null;
  }
  
  /**
   * Check if required efixes are installed
   */
  private MonitoringResult checkEfixes(SailPointContext context, String sailpointVersion,
                                       ComplianceData data) throws Exception {
    if (data.requiredEfixes.isEmpty()) {
      return new MonitoringResult(getName() + "Efix", Status.OK,
              "No efixes required for version " + sailpointVersion)
              .addDetail("requiredEfixes", "none");
    }
    
    // Get installed patches from SailPoint
    List<String> installedPatches = getInstalledPatches(context);
    
    List<String> missingEfixes = new ArrayList<>();
    for (String efix : data.requiredEfixes) {
      if (!installedPatches.contains(efix)) {
        missingEfixes.add(efix);
      }
    }
    
    if (missingEfixes.isEmpty()) {
      return new MonitoringResult(getName() + "Efix", Status.OK,
              "All required efixes installed: " + String.join(", ", data.requiredEfixes))
              .addDetail("installedEfixes", data.requiredEfixes);
    } else {
      return new MonitoringResult(getName() + "Efix", Status.ERROR,
              "Missing required efixes: " + String.join(", ", missingEfixes))
              .addDetail("required", data.requiredEfixes)
              .addDetail("missing", missingEfixes);
    }
  }
  
  /**
   * Check if Java version meets requirements
   */
  private MonitoringResult checkJavaVersion(ComplianceData data) {
    String javaVersion = System.getProperty("java.version");
    int javaMajorVersion = getMajorJavaVersion(javaVersion);
    
    // Find requirement for current major version
    JavaRequirement requirement = null;
    for (JavaRequirement req : data.javaRequirements) {
      if (req.majorVersion == javaMajorVersion) {
        requirement = req;
        break;
      }
    }
    
    if (requirement == null) {
      return new MonitoringResult(getName() + "Java", Status.WARNING,
              "No Java requirement found for major version " + javaMajorVersion)
              .addDetail("installedJavaVersion", javaVersion)
              .addDetail("installedMajor", javaMajorVersion);
    }
    
    int comparison = compareVersions(javaVersion, requirement.minimumVersion);
    
    if (comparison >= 0) {
      return new MonitoringResult(getName() + "Java", Status.OK,
              "Java version complies: " + javaVersion + " >= " + requirement.minimumVersion)
              .addDetail("installedVersion", javaVersion)
              .addDetail("requiredMinimum", requirement.minimumVersion);
    } else {
      return new MonitoringResult(getName() + "Java", Status.ERROR,
              "Java version non-compliant: " + javaVersion + " < " + requirement.minimumVersion)
              .addDetail("installedVersion", javaVersion)
              .addDetail("requiredMinimum", requirement.minimumVersion);
    }
  }
  
  /**
   * Check if Tomcat version meets requirements
   */
  private MonitoringResult checkTomcatVersion(SailPointContext context, ComplianceData data) {
    if (data.requiredTomcatVersion == null) {
      return new MonitoringResult(getName() + "Tomcat", Status.OK,
              "No Tomcat requirement specified")
              .addDetail("requirement", "none");
    }
    
    String tomcatVersion = getTomcatVersion();
    
    if (tomcatVersion == null) {
      return new MonitoringResult(getName() + "Tomcat", Status.WARNING,
              "Could not determine Tomcat version")
              .addDetail("required", data.requiredTomcatVersion);
    }
    
    int comparison = compareVersions(tomcatVersion, data.requiredTomcatVersion);
    
    if (comparison >= 0) {
      return new MonitoringResult(getName() + "Tomcat", Status.OK,
              "Tomcat version complies: " + tomcatVersion + " >= " + data.requiredTomcatVersion)
              .addDetail("installedVersion", tomcatVersion)
              .addDetail("requiredMinimum", data.requiredTomcatVersion);
    } else {
      return new MonitoringResult(getName() + "Tomcat", Status.ERROR,
              "Tomcat version non-compliant: " + tomcatVersion + " < " + data.requiredTomcatVersion)
              .addDetail("installedVersion", tomcatVersion)
              .addDetail("requiredMinimum", data.requiredTomcatVersion);
    }
  }
  
  /**
   * Get installed patches/efixes from SailPoint
   */
  private List<String> getInstalledPatches(SailPointContext context) {
    // This would need to be implemented based on your SailPoint setup
    // Typically patches are stored in the database or file system
    // For now, returning empty list - customize based on your setup
    List<String> patches = new ArrayList<>();
    
    try {
      // Example: query Configuration object for patch list
      Configuration config = context.getObjectByName(Configuration.class, "default");
      if (config != null) {
        // Implement patch detection logic here
      }
    } catch (Exception e) {
      log.warn("Could not retrieve installed patches: " + e.getMessage());
    }
    
    return patches;
  }
  
  /**
   * Get Tomcat version from environment
   */
  private String getTomcatVersion() {
    Class<?> serverInfoClass = null;
    try {
      serverInfoClass = Class.forName("org.apache.catalina.util.ServerInfo");
      
      java.lang.reflect.Method getServerInfoMethod = serverInfoClass.getMethod("getServerInfo");
      String info = (String) getServerInfoMethod.invoke(null);
      if (info != null) {
        log.debug("ServerInfo via reflection: " + info);
        Pattern pattern = Pattern.compile("Apache Tomcat/([\\d.]+)");
        Matcher matcher = pattern.matcher(info);
        if (matcher.find()) {
          String version = matcher.group(1);
          log.debug("Tomcat version detected via reflection: " + version);
          return version;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    return null;
  }
  
  /**
   * Get major Java version (11, 17, 21, etc.)
   */
  private int getMajorJavaVersion(String javaVersion) {
    // Parse version like "11.0.21" or "21.0.1"
    Pattern pattern = Pattern.compile("(\\d+)");
    Matcher matcher = pattern.matcher(javaVersion);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    return -1;
  }
  
  /**
   * Compare two version strings (e.g., "11.0.21" vs "11.0.20")
   * Returns: > 0 if v1 > v2, 0 if equal, < 0 if v1 < v2
   */
  private int compareVersions(String v1, String v2) {
    String[] parts1 = v1.split("\\.");
    String[] parts2 = v2.split("\\.");
    
    int maxLength = Math.max(parts1.length, parts2.length);
    
    for (int i = 0; i < maxLength; i++) {
      int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
      int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
      
      if (num1 > num2) return 1;
      if (num1 < num2) return -1;
    }
    
    return 0;
  }
  
  /**
   * Helper classes
   */
  private static class ComplianceData {
    String sailpointVersion;
    List<String> requiredEfixes = new ArrayList<>();
    List<JavaRequirement> javaRequirements = new ArrayList<>();
    String requiredTomcatVersion;
  }
  
  private static class JavaRequirement {
    int majorVersion;
    String minimumVersion;
  }
}