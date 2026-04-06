package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult.CheckResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Checks IdentityRequest and ProvisioningTransaction objects for failures.
 */
public class FailedProvisioningCheck implements IMonitoringCheck {

    private static final Log log = LogFactory.getLog(FailedProvisioningCheck.class);

    private final long lookbackMs;

    public FailedProvisioningCheck() {
        this(24 * 60 * 60 * 1000L);
    }

    public FailedProvisioningCheck(long lookbackMs) {
        this.lookbackMs = lookbackMs;
    }

    @Override
    public String getName() { return "FailedProvisioning"; }

    @Override
    public CheckResult execute(SailPointContext context) throws GeneralException {
        Date since = new Date(System.currentTimeMillis() - lookbackMs);
        int failedCount = 0;
        List<Map<String, Object>> failedList = new ArrayList<>();

        // ── ProvisioningTransaction failures ──────────────────────────────
        try {
            QueryOptions qo = new QueryOptions();
            qo.addFilter(Filter.gt("created", since));
            qo.addFilter(Filter.eq("status",
                    ProvisioningTransaction.Status.Failed));

            List<ProvisioningTransaction> txList =
                    context.getObjects(ProvisioningTransaction.class, qo);

            if (txList != null) {
                failedCount += txList.size();
                for (ProvisioningTransaction tx : txList) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("type",          "ProvisioningTransaction");
                    entry.put("identityName",  tx.getIdentityName());
                    entry.put("applicationName", tx.getApplicationName());
                    entry.put("operation",     tx.getOperation() != null
                                                  ? tx.getOperation() : "");
                    entry.put("created",       tx.getCreated() != null
                                                  ? tx.getCreated().toString() : "");
                    failedList.add(entry);
                }
            }
        } catch (Exception e) {
            log.warn("Could not query ProvisioningTransaction: " + e.getMessage());
        }

        // ── IdentityRequest failures ───────────────────────────────────────
        try {
            QueryOptions qo = new QueryOptions();
            qo.addFilter(Filter.gt("created", since));
            qo.addFilter(Filter.eq("completionStatus",
                    IdentityRequest.CompletionStatus.Failure));

            List<IdentityRequest> irList =
                    context.getObjects(IdentityRequest.class, qo);

            if (irList != null) {
                failedCount += irList.size();
                for (IdentityRequest ir : irList) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("type",         "IdentityRequest");
                    entry.put("targetName",   ir.getTargetDisplayName());
                    entry.put("requestType",  ir.getType());
                    entry.put("created",      ir.getCreated() != null
                                                 ? ir.getCreated().toString() : "");
                    failedList.add(entry);
                }
            }
        } catch (Exception e) {
            log.warn("Could not query IdentityRequest: " + e.getMessage());
        }

        if (failedCount == 0) {
            return new CheckResult(getName(), Status.OK,
                    "No failed provisioning actions in the last 24 hours")
                    .addDetail("failedCount", 0);
        }

        return new CheckResult(getName(), Status.ERROR,
                failedCount + " failed provisioning action(s) detected")
                .addDetail("failedCount", failedCount)
                .addDetail("failedItems", failedList);
    }
}
