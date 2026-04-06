package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult.CheckResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Verifies that the SailPoint IIQ environment is reachable and responsive
 * by executing a trivial database query (fetch the 'spadmin' identity).
 */
public class HealthPingCheck implements IMonitoringCheck {

    private static final Log log = LogFactory.getLog(HealthPingCheck.class);

    @Override
    public String getName() { return "HealthPing"; }

    @Override
    public CheckResult execute(SailPointContext context) {
        long start = System.currentTimeMillis();
        try {
            // A minimal DB round-trip: fetch spadmin (always exists)
            Identity spadmin = context.getObjectByName(Identity.class, "spadmin");
            long elapsed = System.currentTimeMillis() - start;

            if (spadmin != null) {
                return new CheckResult(getName(), Status.OK,
                        "Environment is healthy (response " + elapsed + " ms)")
                        .addDetail("responseTimeMs", elapsed);
            } else {
                return new CheckResult(getName(), Status.WARNING,
                        "spadmin not found – environment may be mis-configured")
                        .addDetail("responseTimeMs", elapsed);
            }
        } catch (Exception e) {
            log.error("HealthPingCheck failed", e);
            return new CheckResult(getName(), Status.ERROR,
                    "Health ping failed: " + e.getMessage())
                    .addDetail("exception", e.getClass().getSimpleName());
        }
    }
}
