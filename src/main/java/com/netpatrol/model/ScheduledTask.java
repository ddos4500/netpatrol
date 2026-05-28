package com.netpatrol.model;

import java.time.LocalTime;

public class ScheduledTask {
    private String name;
    private String taskType = "DAILY";
    private LocalTime runTime;
    private LocalTime endTime;
    private int intervalMinutes = 5;
    private boolean includeDeviceCheck = false;
    private boolean includePortCheck = true;
    private boolean includeSnmpOidCheck = false;
    private boolean enabled = true;
    private boolean running = false;
    private transient LocalTime nextRunTime;

    public ScheduledTask() {
    }

    public ScheduledTask(String name, LocalTime runTime, boolean enabled) {
        this.name = name;
        this.runTime = runTime;
        this.enabled = enabled;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public LocalTime getRunTime() { return runTime; }
    public void setRunTime(LocalTime runTime) { this.runTime = runTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }
    public boolean isIncludeDeviceCheck() { return includeDeviceCheck; }
    public void setIncludeDeviceCheck(boolean includeDeviceCheck) { this.includeDeviceCheck = includeDeviceCheck; }
    public boolean isIncludePortCheck() { return includePortCheck; }
    public void setIncludePortCheck(boolean includePortCheck) { this.includePortCheck = includePortCheck; }
    public boolean isIncludeSnmpOidCheck() { return includeSnmpOidCheck; }
    public void setIncludeSnmpOidCheck(boolean includeSnmpOidCheck) { this.includeSnmpOidCheck = includeSnmpOidCheck; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public LocalTime getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(LocalTime nextRunTime) { this.nextRunTime = nextRunTime; }

    public boolean isIntervalTask() {
        return "INTERVAL".equalsIgnoreCase(taskType);
    }
}
