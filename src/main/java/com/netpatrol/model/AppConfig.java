package com.netpatrol.model;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    private final List<Device> devices = new ArrayList<Device>();
    private final List<PortMonitor> portMonitors = new ArrayList<PortMonitor>();
    private final List<SnmpOidMonitor> snmpOidMonitors = new ArrayList<SnmpOidMonitor>();
    private final List<ScheduledTask> scheduledTasks = new ArrayList<ScheduledTask>();
    private final List<MqttTopicRule> mqttTopicRules = new ArrayList<MqttTopicRule>();
    private MqttConfig mqttConfig = new MqttConfig();
    private DatabaseConfig databaseConfig = new DatabaseConfig();

    public List<Device> getDevices() { return devices; }
    public List<PortMonitor> getPortMonitors() { return portMonitors; }
    public List<SnmpOidMonitor> getSnmpOidMonitors() { return snmpOidMonitors; }
    public List<ScheduledTask> getScheduledTasks() { return scheduledTasks; }
    public List<MqttTopicRule> getMqttTopicRules() { return mqttTopicRules; }
    public MqttConfig getMqttConfig() { return mqttConfig; }
    public void setMqttConfig(MqttConfig mqttConfig) { this.mqttConfig = mqttConfig; }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public void setDatabaseConfig(DatabaseConfig databaseConfig) { this.databaseConfig = databaseConfig; }
}
