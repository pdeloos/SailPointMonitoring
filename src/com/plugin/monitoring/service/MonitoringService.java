package com.plugin.monitoring.service;

import com.plugin.monitoring.checks.*;
import com.plugin.monitoring.model.MonitoringResult;
import com.plugin.monitoring.service.WebServiceSenderService.WebServiceException;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.server.BasePluginService;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class MonitoringService extends BasePluginService {
  
  private static final Log log = LogFactory.getLog(MonitoringService.class);
  
  public static final String CONFIG_OBJECT = "MonitoringPluginConfig";
  
  private final VelocityTemplateService velocityService = new VelocityTemplateService();
  
  @Override
  public String getPluginName() {
    return "SailPointMonitoring";
  }
  
  /**
   * Override the configure method to handle setup of our Service. Here
   * we'll use one of the getSetting helper methods to pull values from
   * our plugin settings
   */
  @Override
  
  public void configure(SailPointContext context) throws GeneralException {
    //mySetting = getSettingString("mySetting");
  }
  
  /**
   * Write our execute method to do some cool stuff
   */
  @Override
  public void execute(SailPointContext context) throws GeneralException {
    Custom cfg = context.getObjectByName(Custom.class, CONFIG_OBJECT);
    log.error("RUNNING NOW");
    if (cfg == null) {
      log.error("MonitoringPluginConfig Custom object not found – aborting");
      return;
    }
    
    Attributes<String, Object> attrs = cfg.getAttributes();
    
    // ── Collect & execute checks ──────────────────────────────────────
    MonitoringResult result = new MonitoringResult();
    List<IMonitoringCheck> checks = buildChecks(attrs);
    
    for (IMonitoringCheck check : checks) {
      try {
        MonitoringResult.CheckResult cr = check.execute(context);
        result.addCheckResult(cr);
        log.debug("Check [" + check.getName() + "] → " + cr.getStatus()
                + ": " + cr.getMessage());
      } catch (Exception e) {
        log.error("Unexpected error executing check " + check.getName(), e);
        result.addCheckResult(new MonitoringResult.CheckResult(
                check.getName(),
                MonitoringResult.Status.ERROR,
                "Check threw unexpected exception: " + e.getMessage()));
      }
    }
    
    // ── Render body ───────────────────────────────────────────────────
    String template = getString(attrs, "bodyTemplate", "");
    if (template.isEmpty()) {
      log.warn("bodyTemplate is empty – sending empty body");
    }
    
    String body;
    try {
      body = velocityService.render(template, result);
    } catch (Exception e) {
      log.error("Velocity rendering failed", e);
      body = "{\"error\":\"Velocity rendering failed: " + e.getMessage() + "\"}";
    }
    
    // ── Send ──────────────────────────────────────────────────────────
    String url = getSettingString("webserviceBaseUrl");
    String user = getString(attrs, "webserviceUsername", "");
    String pass = getString(attrs, "webservicePassword", "");
    String ctype = getString(attrs, "webserviceContentType", "application/json");
    int timeout = getInt(attrs, "webserviceTimeoutSeconds", 30);
    
    if (url.isEmpty()) {
      log.error("webserviceBaseUrl is not configured – monitoring payload discarded");
      return;
    }
    
    WebServiceSenderService sender =
            new WebServiceSenderService(url, user, pass, ctype, timeout);
    try {
      sender.send(body);
    } catch (WebServiceException e) {
      log.error("Failed to send monitoring payload: " + e.getMessage(), e);
    }
  }
  
  
  // -------------------------------------------------------
  private List<IMonitoringCheck> buildChecks(Attributes<String, Object> attrs) {
    List<IMonitoringCheck> list = new ArrayList<>();
    
    if (getBool(attrs, "enableHealthPing", true)) {
      list.add(new HealthPingCheck());
    }
    if (getBool(attrs, "enableFailedTasks", true)) {
      list.add(new FailedTasksCheck());
    }
    if (getBool(attrs, "enableFailedProvisioning", true)) {
      list.add(new FailedProvisioningCheck());
    }
    if (getBool(attrs, "enableProvisioningDelta", true)) {
      int wPct = getInt(attrs, "provisioningDeltaWarningPct", 50);
      int ePct = getInt(attrs, "provisioningDeltaErrorPct", 80);
      list.add(new ProvisioningDeltaCheck(wPct, ePct));
    }
    if (getBool(attrs, "enableApplicationHealth", true)) {
      List<String> inc = csvToList(getString(attrs, "applicationHealthIncluded", ""));
      List<String> exc = csvToList(getString(attrs, "applicationHealthExcluded", ""));
      list.add(new ApplicationHealthCheck(inc, exc));
    }
    
    // Custom BeanShell rules: each entry is "ruleName|displayName"
    Object rawRules = attrs.get("customRules");
    if (rawRules instanceof List) {
      for (Object entry : (List<?>) rawRules) {
        if (entry == null) continue;
        String[] parts = entry.toString().split("\\|", 2);
        String ruleName = parts[0].trim();
        String displayName = parts.length > 1 ? parts[1].trim() : ruleName;
        if (!ruleName.isEmpty()) {
          list.add(new BeanshellCustomCheck(ruleName, displayName));
        }
      }
    }
    
    return list;
  }
  
  // ── Helpers ──────────────────────────────────────────────────────────
  private String getString(Attributes<String, Object> a, String key, String def) {
    Object v = a.get(key);
    return (v != null) ? v.toString() : def;
  }
  
  private boolean getBool(Attributes<String, Object> a, String key, boolean def) {
    Object v = a.get(key);
    if (v == null) return def;
    if (v instanceof Boolean) return (Boolean) v;
    return Boolean.parseBoolean(v.toString());
  }
  
  private int getInt(Attributes<String, Object> a, String key, int def) {
    Object v = a.get(key);
    if (v == null) return def;
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return def;
    }
  }
  
  private List<String> csvToList(String csv) {
    List<String> list = new ArrayList<>();
    if (csv == null || csv.trim().isEmpty()) return list;
    for (String s : csv.split(",")) {
      String t = s.trim();
      if (!t.isEmpty()) list.add(t);
    }
    return list;
  }
  
  
}
