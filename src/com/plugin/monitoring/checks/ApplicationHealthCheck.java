package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
import org.apache.commons.lang3.StringUtils;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Calls testConfiguration() on each Application that has a connector defined.
 * Applications can be explicitly included or excluded via the plugin configuration.
 */
public class ApplicationHealthCheck implements IMonitoringCheck {
  
  private static final Log log = LogFactory.getLog(ApplicationHealthCheck.class);
  
  /**
   * Applications to skip, regardless of includedApps
   */
  private final List<String> excludedApps;
  
  public ApplicationHealthCheck(List<String> excludedApps) {
    this.excludedApps = excludedApps != null ? excludedApps : Collections.emptyList();
  }
  
  @Override
  public String getName() {
    return "ApplicationHealth";
  }
  
  @Override
  public List<MonitoringResult> execute(SailPointContext context) throws GeneralException {
    QueryOptions qo = new QueryOptions();
    
    List<MonitoringResult> results = new ArrayList<>();
    
    List<Application> apps = context.getObjects(Application.class, qo);
    
    int totalChecked = 0;
    int totalFailed = 0;
    
    for (Application app : apps) {
      Status status = Status.OK;
      
      if (excludedApps.contains(app.getName())) continue;
      // Skip apps without a real connector (e.g. logical apps)
      if (app.getConnector() == null || app.getConnector().isEmpty()) continue;
      
      totalChecked++;
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("applicationName", app.getName());
      
      try {
        Connector connector = ConnectorFactory.getConnector(app, null);
        connector.testConfiguration();
        
        results.add(new MonitoringResult(getName(), Status.OK, "")
                .addDetail("applicationName", app.getName())
        );
      } catch (Exception e) {
        totalFailed++;
        results.add(new MonitoringResult(getName(), Status.ERROR, escapePrometheusLabelValue(e.getMessage()))
                .addDetail("applicationName", app.getName()));
        log.warn("Application health check failed for " + app.getName() + ": " + e.getMessage());
      }
    }
    
    return results;
  }
  
  public static String escapePrometheusLabelValue(Object value) {
    if (value == null) return "";
    String s = String.valueOf(value);
    s = StringUtils.normalizeSpace(s);        // verwijder newlines e.d.
    s = s.replace("\\", "\\\\");             // escape backslash
    s = s.replace("\"", "\\\"");             // escape quote
    return StringUtils.normalizeSpace(s);
  }
}
