package com.plugin.monitoring.checks;

import com.plugin.monitoring.model.MonitoringResult;
import com.plugin.monitoring.model.MonitoringResult.Status;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Checks for TaskResult objects in an error / terminated state.
 * By default it looks at results from the last 24 hours.
 */
public class FailedTasksCheck implements IMonitoringCheck {

    private static final Log log = LogFactory.getLog(FailedTasksCheck.class);

    /** How far back to look (milliseconds).  Default: 24 h */
    private final long lookbackMs;

    public FailedTasksCheck() {
        this(24 * 60 * 60 * 1000L);
    }

    public FailedTasksCheck(long lookbackMs) {
        this.lookbackMs = lookbackMs;
    }

    @Override
    public String getName() { return "FailedTasks"; }

    @Override
    public List<MonitoringResult> execute(SailPointContext context) throws GeneralException {
        Date since = new Date(System.currentTimeMillis() - lookbackMs);

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.gt("created", since));
        qo.addFilter(Filter.or(
            Filter.eq("completionStatus", TaskResult.CompletionStatus.Error),
            Filter.eq("completionStatus", TaskResult.CompletionStatus.Terminated)
        ));
        qo.setOrderBy("created");
        qo.setOrderAscending(false);

        List<TaskResult> results = context.getObjects(TaskResult.class, qo);

        if (results == null || results.isEmpty()) {
            return List.of(new MonitoringResult(getName(), Status.OK,
                    "No failed tasks in the last 24 hours")
                    .addDetail("failedCount", 0));
        }

        List<Map<String, Object>> failedList = new ArrayList<>();
        for (TaskResult tr : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name",     tr.getName());
            entry.put("status",   tr.getCompletionStatus() != null
                                     ? tr.getCompletionStatus().name() : "Unknown");
            entry.put("created",  tr.getCreated() != null
                                     ? tr.getCreated().toString() : "");
            entry.put("messages", tr.getMessages() != null
                                     ? tr.getMessages().toString() : "");
            failedList.add(entry);
        }

        return List.of(new MonitoringResult(getName(), Status.ERROR,
                results.size() + " failed task(s) detected in the last 24 hours")
                .addDetail("failedCount", results.size())
                .addDetail("failedTasks", failedList));
    }
}
