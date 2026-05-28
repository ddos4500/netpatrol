package com.netpatrol.model;

public class MqttConfig {
    private String host;
    private int port = 1883;
    private String username;
    private String password;
    private String topic;
    private String heartbeatTopic;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getHeartbeatTopic() { return heartbeatTopic; }
    public void setHeartbeatTopic(String heartbeatTopic) { this.heartbeatTopic = heartbeatTopic; }
}
