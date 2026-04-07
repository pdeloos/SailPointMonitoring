package com.plugin.monitoring.model;

import java.util.*;

/**
 * Holds the result of a full monitoring cycle.
 */
public class MonitoringResult {
  
  public enum Status {OK, WARNING, ERROR, UNKNOWN}
  
  private Date timestamp;
  private String checkName;
  private Status status;
  private String message;
  private Map<String, Object> details = new LinkedHashMap<>();
  
  public MonitoringResult() {
    this.timestamp = new Date();
    this.status = Status.OK;
  }
  
  public MonitoringResult(String checkName, Status status, String message) {
    this.timestamp = new Date();
    this.checkName = checkName;
    this.status = status;
    this.message = message;
  }
  
  public Date getTimestamp() {
    return timestamp;
  }
  
  public Status getStatus() {
    return status;
  }
  
  public MonitoringResult addDetail(String key, Object value) {
    details.put(key, value);
    return this;
  }
  
  public String getCheckName() {
    return checkName;
  }
  
  public String getMessage() {
    return message;
  }
  
  public Map<String, Object> getDetails() {
    return details;
  }
  
}