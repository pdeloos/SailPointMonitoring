package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult;
import sailpoint.api.SailPointContext;

import java.util.List;

/**
 * Contract that every monitoring check must fulfil.
 */
public interface IMonitoringCheck {

    /**
     * Human-readable name shown in reports and the plugin UI.
     */
    String getName();

    /**
     * Execute the check and return a single {@link MonitoringResult}.
     *
     * @param context live SailPoint context (never null)
     * @return result – never null
     */
    List<MonitoringResult> execute(SailPointContext context) throws Exception;
}
