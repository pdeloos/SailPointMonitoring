package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult.CheckResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
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

    /** Applications to check; empty list = all applications */
    private final List<String> includedApps;
    /** Applications to skip, regardless of includedApps */
    private final List<String> excludedApps;

    public ApplicationHealthCheck(List<String> includedApps,
                                   List<String> excludedApps) {
        this.includedApps = includedApps  != null ? includedApps  : Collections.emptyList();
        this.excludedApps = excludedApps  != null ? excludedApps  : Collections.emptyList();
    }

    @Override
    public String getName() { return "ApplicationHealth"; }

    @Override
    public CheckResult execute(SailPointContext context) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        if (!includedApps.isEmpty()) {
            qo.addFilter(Filter.in("name", includedApps));
        }

        List<Application> apps = context.getObjects(Application.class, qo);

        int totalChecked = 0;
        int totalFailed  = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Application app : apps) {
            if (excludedApps.contains(app.getName())) continue;
            // Skip apps without a real connector (e.g. logical apps)
            if (app.getConnector() == null || app.getConnector().isEmpty()) continue;

            totalChecked++;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("applicationName", app.getName());
            entry.put("connector",       app.getConnector());

            try {
                Connector connector = ConnectorFactory.getConnector(app, null);
                connector.testConfiguration();
                entry.put("status",  "OK");
                entry.put("message", "Test connection succeeded");
            } catch (Exception e) {
                totalFailed++;
                entry.put("status",  "ERROR");
                entry.put("message", e.getMessage());
                log.warn("Application health check failed for " + app.getName()
                        + ": " + e.getMessage());
            }
            results.add(entry);
        }

        Status status;
        String message;
        if (totalFailed == 0) {
            status  = Status.OK;
            message = "All " + totalChecked + " application(s) healthy";
        } else if (totalFailed < totalChecked) {
            status  = Status.WARNING;
            message = totalFailed + "/" + totalChecked + " application(s) failed test connection";
        } else {
            status  = Status.ERROR;
            message = "All " + totalChecked + " application(s) failed test connection";
        }

        return new CheckResult(getName(), status, message)
                .addDetail("totalChecked", totalChecked)
                .addDetail("totalFailed",  totalFailed)
                .addDetail("applications", results);
    }
}
