package com.plugin.monitoring.service;

import com.plugin.monitoring.model.MonitoringResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;

import java.io.StringWriter;
import java.util.*;

/**
 * Renders an Apache Velocity template against the current {@link MonitoringResult}.
 *
 * <h3>Available template variables</h3>
 * <pre>
 * $result.overallStatus          – OK / WARNING / ERROR / UNKNOWN
 * $result.timestamp              – java.util.Date
 * $result.checkResults           – List of CheckResult
 * $checks                        – Map&lt;checkName, CheckResult&gt; for easy named access
 * $now                           – alias for $result.timestamp (formatted ISO string)
 * </pre>
 *
 * <h3>Example template</h3>
 * <pre>{@code
 * {
 *   "status": "$result.overallStatus",
 *   "timestamp": "$now",
 *   "checks": {
 *     #foreach($check in $result.checkResults)
 *     "$check.checkName": {
 *       "status": "$check.status",
 *       "message": "$check.message"
 *     }#if($foreach.hasNext),#end
 *     #end
 *   }
 * }
 * }</pre>
 */
public class VelocityTemplateService {
  
  private static final Log log = LogFactory.getLog(VelocityTemplateService.class);
  
  private static final String ISO_FMT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  
  private final VelocityEngine engine;
  
  public VelocityTemplateService() {
    engine = new VelocityEngine();
    //engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.Log4JLogChute");
    engine.setProperty("runtime.log.logsystem.log4j.logger",
            VelocityTemplateService.class.getName());
    try {
      engine.init();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialise VelocityEngine", e);
    }
  }
  
  /**
   * Renders {@code template} with the given monitoring result.
   *
   * @param template Velocity template string
   * @param result   monitoring result to expose
   * @return rendered string
   */
  public String render(String template, MonitoringResult result) throws Exception {
    VelocityContext vc = buildContext(result);
    StringWriter out = new StringWriter();
    engine.evaluate(vc, out, "MonitoringPlugin", template);
    return out.toString();
  }
  
  // -------------------------------------------------------
  private VelocityContext buildContext(MonitoringResult result) {
    VelocityContext vc = new VelocityContext();
    
    vc.put("result", result);
    
    // Formatted ISO timestamp
    java.text.SimpleDateFormat sdf =
            new java.text.SimpleDateFormat(ISO_FMT, Locale.US);
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    vc.put("now", sdf.format(result.getTimestamp()));
    
    // Utility
    vc.put("newline", "\n");
    
    return vc;
  }
}
