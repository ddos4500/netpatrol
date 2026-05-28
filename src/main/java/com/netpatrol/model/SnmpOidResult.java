package com.netpatrol.model;

import java.time.LocalDateTime;

public class SnmpOidResult {
    private final SnmpOidMonitor monitor;
    private final boolean success;
    private final String value;
    private final String variableType;
    private final String message;
    private final LocalDateTime checkTime;

    public SnmpOidResult(SnmpOidMonitor monitor, boolean success, String value, String variableType, String message, LocalDateTime checkTime) {
        this.monitor = monitor;
        this.success = success;
        this.value = value;
        this.variableType = variableType;
        this.message = message;
        this.checkTime = checkTime;
    }

    public SnmpOidMonitor getMonitor() { return monitor; }
    public boolean isSuccess() { return success; }
    public String getValue() { return value; }
    public String getVariableType() { return variableType; }
    public String getMessage() { return message; }
    public LocalDateTime getCheckTime() { return checkTime; }
}
