package com.netpatrol.model;

import java.time.LocalDateTime;

public class CheckResult {
    private final Device device;
    private final boolean online;
    private final long responseTimeMs;
    private final String failureReason;
    private final LocalDateTime checkTime;

    public CheckResult(Device device, boolean online, long responseTimeMs, String failureReason, LocalDateTime checkTime) {
        this.device = device;
        this.online = online;
        this.responseTimeMs = responseTimeMs;
        this.failureReason = failureReason;
        this.checkTime = checkTime;
    }

    public Device getDevice() { return device; }
    public boolean isOnline() { return online; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCheckTime() { return checkTime; }
}
