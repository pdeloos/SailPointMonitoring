package com.plugin.monitoring.model;

import java.util.*;

/**
 * Holds the result of a full monitoring cycle.
 */
public class MonitoringResult {

    public enum Status { OK, WARNING, ERROR, UNKNOWN }

    private Date timestamp;
    private Status overallStatus;
    private List<CheckResult> checkResults = new ArrayList<>();

    public MonitoringResult() {
        this.timestamp = new Date();
        this.overallStatus = Status.OK;
    }

    public void addCheckResult(CheckResult result) {
        checkResults.add(result);
        // Escalate overall status
        if (result.getStatus().ordinal() > overallStatus.ordinal()) {
            overallStatus = result.getStatus();
        }
    }

    public Date getTimestamp()              { return timestamp; }
    public Status getOverallStatus()        { return overallStatus; }
    public List<CheckResult> getCheckResults() { return checkResults; }

    // -------------------------------------------------------
    // Inner class
    // -------------------------------------------------------
    public static class CheckResult {
        private String checkName;
        private Status status;
        private String message;
        private Map<String, Object> details = new LinkedHashMap<>();

        public CheckResult(String checkName, Status status, String message) {
            this.checkName = checkName;
            this.status    = status;
            this.message   = message;
        }

        public CheckResult addDetail(String key, Object value) {
            details.put(key, value);
            return this;
        }

        public String getCheckName()              { return checkName; }
        public Status getStatus()                 { return status; }
        public String getMessage()                { return message; }
        public Map<String, Object> getDetails()   { return details; }
    }
}
