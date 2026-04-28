package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a named BeanShell Rule that must return a Boolean.
 * <ul>
 *   <li>true  → Status.OK</li>
 *   <li>false → Status.ERROR</li>
 *   <li>exception → Status.ERROR</li>
 * </ul>
 * <p>
 * The rule receives a {@code context} variable (SailPointContext).
 * <p>
 * Configure one instance of this class per custom Rule in the plugin config.
 */
public class BeanshellCustomCheck implements IMonitoringCheck {
  
  private static final Log log = LogFactory.getLog(BeanshellCustomCheck.class);
  
  private final String ruleName;
  private final String displayName;
  
  /**
   * @param ruleName    Name of the IIQ Rule object to execute
   * @param displayName Human-readable label for this check
   */
  public BeanshellCustomCheck(String ruleName, String displayName) {
    this.ruleName = ruleName;
    this.displayName = displayName;
  }
  
  @Override
  public String getName() {
    return "CustomCheck_" + (displayName != null ? displayName : ruleName);
  }
  
  @Override
  public List<MonitoringResult> execute(SailPointContext context) {
    Rule rule;
    try {
      rule = context.getObjectByName(Rule.class, ruleName);
    } catch (GeneralException e) {
      log.error("Cannot load rule '" + ruleName + "'", e);
      return List.of(new MonitoringResult(getName(), Status.ERROR,
              "Rule '" + ruleName + "' could not be loaded: " + e.getMessage()));
    }
    
    if (rule == null) {
      return List.of(new MonitoringResult(getName(), Status.ERROR,
              "Rule '" + ruleName + "' not found in IIQ"));
    }
    
    Map<String, Object> args = new HashMap<>();
    args.put("context", context);
    
    try {
      Object result = context.runRule(rule, args);
      
      if (result instanceof Boolean) {
        boolean ok = (Boolean) result;
        return List.of(new MonitoringResult(getName(),
                ok ? Status.OK : Status.ERROR,
                ok ? "Custom check passed" : "Custom check returned false")
                .addDetail("ruleName", ruleName)
                .addDetail("ruleResult", ok));
      } else if (result instanceof MonitoringResult) {
        return List.of((MonitoringResult) result);
      } else {
        return List.of(new MonitoringResult(getName(), Status.WARNING,
                "Rule did not return a Boolean or MonitoringResult (returned: "
                        + (result == null ? "null" : result.getClass().getSimpleName()) + ")")
                .addDetail("ruleName", ruleName)
                .addDetail("ruleResult", String.valueOf(result)));
      }
    } catch (Exception e) {
      log.error("BeanshellCustomCheck failed for rule '" + ruleName + "'", e);
      return List.of(new MonitoringResult(getName(), Status.ERROR,
              "Rule execution failed: " + e.getMessage())
              .addDetail("ruleName", ruleName)
              .addDetail("exception", e.getClass().getSimpleName()));
    }
  }
}