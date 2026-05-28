package com.netpatrol.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InspectionSnapshot {
    private String taskName;
    private String triggerType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private final List<CheckResult> deviceResults = new ArrayList<CheckResult>();
    private final List<PortCheckResult> portResults = new ArrayList<PortCheckResult>();
    private final List<SnmpOidResult> snmpOidResults = new ArrayList<SnmpOidResult>();
    private final List<String> mqttMessages = new ArrayList<String>();

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public List<CheckResult> getDeviceResults() { return deviceResults; }
    public List<PortCheckResult> getPortResults() { return portResults; }
    public List<SnmpOidResult> getSnmpOidResults() { return snmpOidResults; }
    public List<String> getMqttMessages() { return mqttMessages; }
}
