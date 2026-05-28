package com.netpatrol.model;

public class MqttTopicRule {
    public static final String FULL_JSON = "FULL_JSON";
    public static final String DEVICE_SUMMARY = "DEVICE_SUMMARY";
    public static final String OFFLINE_DEVICES = "OFFLINE_DEVICES";
    public static final String PORT_ALERT = "PORT_ALERT";
    public static final String HEARTBEAT = "HEARTBEAT";

    private String name;
    private String messageType = FULL_JSON;
    private String topic;
    private boolean enabled = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
