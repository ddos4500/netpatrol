package com.netpatrol.model;

public class PortMonitor {
    private String switchName;
    private String switchIp;
    private String area;
    private String snmpVersion = "2c";
    private String snmpCommunity = "public";
    private String matchMode = "ifName";
    private String portKey;
    private int alertSpeedMbps = 100;
    private String remark;

    public String getSwitchName() { return switchName; }
    public void setSwitchName(String switchName) { this.switchName = switchName; }
    public String getSwitchIp() { return switchIp; }
    public void setSwitchIp(String switchIp) { this.switchIp = switchIp; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getSnmpVersion() { return snmpVersion; }
    public void setSnmpVersion(String snmpVersion) { this.snmpVersion = snmpVersion; }
    public String getSnmpCommunity() { return snmpCommunity; }
    public void setSnmpCommunity(String snmpCommunity) { this.snmpCommunity = snmpCommunity; }
    public String getMatchMode() { return matchMode; }
    public void setMatchMode(String matchMode) { this.matchMode = matchMode; }
    public String getPortKey() { return portKey; }
    public void setPortKey(String portKey) { this.portKey = portKey; }
    public int getAlertSpeedMbps() { return alertSpeedMbps; }
    public void setAlertSpeedMbps(int alertSpeedMbps) { this.alertSpeedMbps = alertSpeedMbps; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
