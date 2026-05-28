package com.netpatrol.model;

import java.time.LocalDateTime;

public class PortCheckResult {
    private final PortMonitor monitor;
    private final boolean success;
    private final boolean alert;
    private final int ifIndex;
    private final String ifName;
    private final String ifDescr;
    private final long speedMbps;
    private final String message;
    private final LocalDateTime checkTime;

    public PortCheckResult(PortMonitor monitor, boolean success, boolean alert, int ifIndex, String ifName,
                           String ifDescr, long speedMbps, String message, LocalDateTime checkTime) {
        this.monitor = monitor;
        this.success = success;
        this.alert = alert;
        this.ifIndex = ifIndex;
        this.ifName = ifName;
        this.ifDescr = ifDescr;
        this.speedMbps = speedMbps;
        this.message = message;
        this.checkTime = checkTime;
    }

    public PortMonitor getMonitor() { return monitor; }
    public boolean isSuccess() { return success; }
    public boolean isAlert() { return alert; }
    public int getIfIndex() { return ifIndex; }
    public String getIfName() { return ifName; }
    public String getIfDescr() { return ifDescr; }
    public long getSpeedMbps() { return speedMbps; }
    public String getMessage() { return message; }
    public LocalDateTime getCheckTime() { return checkTime; }
}
