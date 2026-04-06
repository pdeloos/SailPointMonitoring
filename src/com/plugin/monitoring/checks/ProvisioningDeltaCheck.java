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
 * Compares the number of provisioning transactions today versus yesterday.
 * A configurable threshold (percentage) triggers a WARNING or ERROR.
 *
 *   warningThresholdPct  – e.g. 50  → warn when today is >50% less than yesterday
 *   errorThresholdPct    – e.g. 80  → error when today is >80% less than yesterday
 */
public class ProvisioningDeltaCheck implements IMonitoringCheck {

    private static final Log log = LogFactory.getLog(ProvisioningDeltaCheck.class);

    private final int warningThresholdPct;
    private final int errorThresholdPct;

    public ProvisioningDeltaCheck(int warningThresholdPct, int errorThresholdPct) {
        this.warningThresholdPct = warningThresholdPct;
        this.errorThresholdPct   = errorThresholdPct;
    }

    @Override
    public String getName() { return "ProvisioningDelta"; }

    @Override
    public CheckResult execute(SailPointContext context) throws GeneralException {
        Calendar cal = Calendar.getInstance();

        // Today: start of today → now
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        Date now        = new Date();

        // Yesterday: start of yesterday → start of today
        cal.add(Calendar.DAY_OF_YEAR, -1);
        Date yesterdayStart = cal.getTime();

        long todayCount     = countTransactions(context, todayStart, now);
        long yesterdayCount = countTransactions(context, yesterdayStart, todayStart);

        // Avoid division by zero: if yesterday had 0, can't compute meaningful delta
        if (yesterdayCount == 0) {
            return new CheckResult(getName(), Status.OK,
                    "No provisioning transactions yesterday – delta check skipped")
                    .addDetail("todayCount",     todayCount)
                    .addDetail("yesterdayCount", 0);
        }

        double dropPct = (1.0 - ((double) todayCount / yesterdayCount)) * 100.0;
        int    dropInt = (int) Math.round(dropPct);

        CheckResult result;
        if (dropPct >= errorThresholdPct) {
            result = new CheckResult(getName(), Status.ERROR,
                    "Provisioning volume dropped " + dropInt + "% compared to yesterday");
        } else if (dropPct >= warningThresholdPct) {
            result = new CheckResult(getName(), Status.WARNING,
                    "Provisioning volume dropped " + dropInt + "% compared to yesterday");
        } else if (dropPct < -50) {
            // Sudden spike – worth noting
            result = new CheckResult(getName(), Status.WARNING,
                    "Provisioning volume spiked " + Math.abs(dropInt) + "% above yesterday");
        } else {
            result = new CheckResult(getName(), Status.OK,
                    "Provisioning volume within normal range (delta: " + dropInt + "%)");
        }

        return result
                .addDetail("todayCount",          todayCount)
                .addDetail("yesterdayCount",       yesterdayCount)
                .addDetail("deltaPercent",         dropInt)
                .addDetail("warningThresholdPct",  warningThresholdPct)
                .addDetail("errorThresholdPct",    errorThresholdPct);
    }

    private long countTransactions(SailPointContext ctx, Date from, Date to)
            throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.ge("created", from));
        qo.addFilter(Filter.lt("created", to));
        return ctx.countObjects(ProvisioningTransaction.class, qo);
    }
}
