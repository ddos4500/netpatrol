package com.netpatrol.model;

public class Device {
    private String name;
    private String type;
    private String ip;
    private String area;
    private String checkMethod;
    private int port;
    private String snmpVersion;
    private String snmpCommunity;
    private String httpUrl;
    private int onvifPort;
    private String username;
    private String password;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getCheckMethod() { return checkMethod; }
    public void setCheckMethod(String checkMethod) { this.checkMethod = checkMethod; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getSnmpVersion() { return snmpVersion; }
    public void setSnmpVersion(String snmpVersion) { this.snmpVersion = snmpVersion; }
    public String getSnmpCommunity() { return snmpCommunity; }
    public void setSnmpCommunity(String snmpCommunity) { this.snmpCommunity = snmpCommunity; }
    public String getHttpUrl() { return httpUrl; }
    public void setHttpUrl(String httpUrl) { this.httpUrl = httpUrl; }
    public int getOnvifPort() { return onvifPort; }
    public void setOnvifPort(int onvifPort) { this.onvifPort = onvifPort; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
