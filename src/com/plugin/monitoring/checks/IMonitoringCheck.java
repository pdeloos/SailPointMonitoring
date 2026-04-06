package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult.CheckResult;
import sailpoint.api.SailPointContext;

/**
 * Contract that every monitoring check must fulfil.
 */
public interface IMonitoringCheck {

    /**
     * Human-readable name shown in reports and the plugin UI.
     */
    String getName();

    /**
     * Execute the check and return a single {@link CheckResult}.
     *
     * @param context live SailPoint context (never null)
     * @return result – never null
     */
    CheckResult execute(SailPointContext context) throws Exception;
}
