package com.plugin.monitoring.service;

import com.plugin.monitoring.checks.*;
import com.plugin.monitoring.model.MonitoringResult;
import com.plugin.monitoring.service.WebServiceSenderService.WebServiceException;
import org.apache.commons.lang3.StringUtils;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.server.BasePluginService;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Util;

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
    log.debug("Start monitoring run");
    
    // ── Collect & execute checks ──────────────────────────────────────
    List<MonitoringResult> results = new ArrayList<MonitoringResult>();
    List<IMonitoringCheck> checks = buildChecks();
    
    for (IMonitoringCheck check : checks) {
      try {
        List<MonitoringResult> subResults = check.execute(context);
        
        for (MonitoringResult result : subResults) {
          log.debug("Check [" + check.getName() + "] " + result.getStatus()
                  + ": " + result.getMessage());
          results.add(result);
        }
      } catch (Exception e) {
        log.error("Unexpected error executing check " + check.getName(), e);
        MonitoringResult result = (new MonitoringResult(
                check.getName(),
                MonitoringResult.Status.ERROR,
                "Check threw unexpected exception: " + e.getMessage()));
        results.add(result);
      }
    }
    
    // ── Render body ───────────────────────────────────────────────────
    String template = getSettingString("bodyTemplate");
    if (Util.isNullOrEmpty(template)) {
      log.warn("bodyTemplate is empty – sending default body");
      template = "<![CDATA[{\n" +
              "  \"source\": \"SailPointIIQ\",\n" +
              "  \"status\": \"$result.overallStatus\",\n" +
              "  \"timestamp\": \"$now\",\n" +
              "  \"checks\": {\n" +
              "#foreach($check in $result.checkResults)\n" +
              "    \"$check.checkName\": {\n" +
              "      \"status\": \"$check.status\",\n" +
              "      \"message\": \"$check.message\"\n" +
              "    }#if($foreach.hasNext),#end\n" +
              "#end\n" +
              "  }\n" +
              "}]]>";
    }
    
    String body;
    
    for (MonitoringResult result : results) {
      try {
        body = velocityService.render(template, result);
        
      } catch (Exception e) {
        log.error("Velocity rendering failed", e);
        body = "{\"error\":\"Velocity rendering failed: " + e.getMessage() + "\"}";
      }
      
      // ── Send ──────────────────────────────────────────────────────────
      String url = getSettingString("webserviceBaseUrl");
      
      //to do: make this configurable
      if ("ApplicationHealth".equals(result.getCheckName())) {
        url += "/application/" + result.getDetails().get("applicationName");
      }
      
      
      String user = getSettingString("webserviceUsername");
      String pass = getSettingString("webservicePassword");
      int timeout = getSettingInt("webserviceTimeoutSeconds");
      
      if (url.isEmpty()) {
        log.error("webserviceBaseUrl is not configured – monitoring payload discarded");
        return;
      }
      
      WebServiceSenderService sender =
              new WebServiceSenderService(url, user, pass, "application/json", timeout);
      try {
        sender.send(body);
        log.debug(body);
      } catch (WebServiceException e) {
        log.error("Failed to send monitoring payload: " + e.getMessage(), e);
      }
    }
  }
  
  
  // -------------------------------------------------------
  private List<IMonitoringCheck> buildChecks() {
    List<IMonitoringCheck> list = new ArrayList<>();
    
    if (getSettingBool("enableHealthPing")) {
      log.debug("Enable health is enabled");
      list.add(new HealthPingCheck());
    }
    if (getSettingBool("enableFailedTasks")) {
      log.debug("Enable failed tasks is enabled");
      list.add(new FailedTasksCheck());
    }
    if (getSettingBool("enableFailedProvisioning")) {
      log.debug("Enable failed provisioning is enabled");
      list.add(new FailedProvisioningCheck());
    }
    if (getSettingBool("enableApplicationHealth")) {
      log.debug("Enable application health is enabled");
      List<String> exc = getSettingMultiString("applicationHealthExcluded");
      list.add(new ApplicationHealthCheck(exc));
    }
    if (getSettingBool("enableServerCompliance")) {
      log.debug("Enable server compliance is enabled");
      String serverComplianceJsonUrl = getSettingString("serverComplianceJsonUrl");
      list.add(new ServerComplianceCheck(serverComplianceJsonUrl));
    }
    
    // Custom BeanShell rules: each entry is "ruleName|displayName"
    List<String> rawRules = getSettingMultiString("customRules");
    for (String ruleName : rawRules) {
      if (Util.isNotNullOrEmpty(ruleName)) {
        String ruleName2 = StringUtils.removeAll(ruleName, "[\\[\\]]");
        log.debug("Executing rule" + ruleName2);
        list.add(new BeanshellCustomCheck(ruleName2, ruleName2));
      }
    }
    
    return list;
  }
}