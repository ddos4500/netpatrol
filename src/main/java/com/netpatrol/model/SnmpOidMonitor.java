package com.netpatrol.model;

public class SnmpOidMonitor {
    private String name;
    private String targetIp;
    private String area;
    private String snmpVersion = "2c";
    private String snmpCommunity = "public";
    private String oid;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getSnmpVersion() { return snmpVersion; }
    public void setSnmpVersion(String snmpVersion) { this.snmpVersion = snmpVersion; }
    public String getSnmpCommunity() { return snmpCommunity; }
    public void setSnmpCommunity(String snmpCommunity) { this.snmpCommunity = snmpCommunity; }
    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
