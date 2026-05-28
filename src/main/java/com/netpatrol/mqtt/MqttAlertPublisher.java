package com.netpatrol.mqtt;

import com.netpatrol.model.MqttConfig;
import com.netpatrol.model.PortCheckResult;
import com.netpatrol.model.PortMonitor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class MqttAlertPublisher {
    private static MqttClient sharedClient;
    private static String sharedKey;

    public void publishPortSpeedAlert(MqttConfig config, PortCheckResult result, String taskName) throws Exception {
        if (isBlank(config.getHost()) || isBlank(config.getTopic())) {
            throw new IllegalArgumentException("MQTT服务器和Topic不能为空");
        }
        publish(config, config.getTopic(), json(result, taskName));
    }

    public void publishInspectionSummary(MqttConfig config, String json) throws Exception {
        if (isBlank(config.getHost()) || isBlank(config.getTopic())) {
            throw new IllegalArgumentException("MQTT服务器和Topic不能为空");
        }
        publish(config, config.getTopic(), json);
    }

    public void publishJson(MqttConfig config, String topic, String json) throws Exception {
        if (isBlank(config.getHost()) || isBlank(topic)) {
            throw new IllegalArgumentException("MQTT服务器和Topic不能为空");
        }
        publish(config, topic, json);
    }

    public void publishHeartbeat(MqttConfig config) throws Exception {
        if (isBlank(config.getHost()) || isBlank(config.getHeartbeatTopic())) {
            throw new IllegalArgumentException("MQTT服务器和心跳Topic不能为空");
        }
        String body = "{"
                + "\"event\":\"netpatrol_heartbeat\","
                + "\"status\":\"alive\","
                + "\"time\":\"" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\""
                + "}";
        publish(config, config.getHeartbeatTopic(), body);
    }

    private static synchronized void publish(MqttConfig config, String topic, String payload) throws Exception {
        String uri = "tcp://" + config.getHost().trim() + ":" + config.getPort();
        String key = uri + "|" + value(config.getUsername()) + "|" + value(config.getPassword());
        if (sharedClient == null || !key.equals(sharedKey)) {
            closeSharedClient();
            sharedClient = new MqttClient(uri, "netpatrol-" + UUID.randomUUID(), new MemoryPersistence());
            sharedKey = key;
        }
        if (!sharedClient.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setKeepAliveInterval(60);
            options.setConnectionTimeout(5);
            if (!isBlank(config.getUsername())) {
                options.setUserName(config.getUsername());
                options.setPassword(config.getPassword() == null ? new char[0] : config.getPassword().toCharArray());
            }
            sharedClient.connect(options);
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        sharedClient.publish(topic, message);
    }

    public static synchronized void closeSharedClient() {
        if (sharedClient == null) return;
        try {
            if (sharedClient.isConnected()) sharedClient.disconnect();
        } catch (Exception ignored) {
        }
        try {
            sharedClient.close();
        } catch (Exception ignored) {
        }
        sharedClient = null;
        sharedKey = null;
    }

    public String buildPortSpeedAlertJson(PortCheckResult result, String taskName) {
        return json(result, taskName);
    }

    private String json(PortCheckResult result, String taskName) {
        PortMonitor m = result.getMonitor();
        return "{"
                + "\"event\":\"switch_port_speed_alert\","
                + "\"taskName\":\"" + escape(taskName) + "\","
                + "\"switchName\":\"" + escape(m.getSwitchName()) + "\","
                + "\"switchIp\":\"" + escape(m.getSwitchIp()) + "\","
                + "\"area\":\"" + escape(m.getArea()) + "\","
                + "\"portName\":\"" + escape(result.getIfName()) + "\","
                + "\"portDescr\":\"" + escape(result.getIfDescr()) + "\","
                + "\"ifIndex\":" + result.getIfIndex() + ","
                + "\"currentSpeedMbps\":" + result.getSpeedMbps() + ","
                + "\"alertSpeedMbps\":" + m.getAlertSpeedMbps() + ","
                + "\"message\":\"" + escape(result.getMessage()) + "\","
                + "\"alertTime\":\"" + result.getCheckTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\""
                + "}";
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
