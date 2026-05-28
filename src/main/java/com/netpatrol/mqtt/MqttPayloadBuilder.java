package com.netpatrol.mqtt;

import com.netpatrol.model.CheckResult;
import com.netpatrol.model.Device;
import com.netpatrol.model.InspectionSnapshot;

import java.time.format.DateTimeFormatter;

public class MqttPayloadBuilder {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String deviceSummary(InspectionSnapshot snapshot) {
        int online = 0;
        for (CheckResult r : snapshot.getDeviceResults()) if (r.isOnline()) online++;
        return "{\n"
                + "  \"event\": \"device_summary\",\n"
                + "  \"taskName\": \"" + escape(snapshot.getTaskName()) + "\",\n"
                + "  \"triggerType\": \"" + escape(snapshot.getTriggerType()) + "\",\n"
                + "  \"deviceSummary\": {\n"
                + "    \"total\": " + snapshot.getDeviceResults().size() + ",\n"
                + "    \"online\": " + online + ",\n"
                + "    \"offline\": " + (snapshot.getDeviceResults().size() - online) + "\n"
                + "  }\n"
                + "}\n";
    }

    public String offlineDevices(InspectionSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"event\": \"offline_devices\",\n");
        json.append("  \"taskName\": \"").append(escape(snapshot.getTaskName())).append("\",\n");
        json.append("  \"triggerType\": \"").append(escape(snapshot.getTriggerType())).append("\",\n");
        json.append("  \"offlineDevices\": [\n");
        int count = 0;
        for (CheckResult r : snapshot.getDeviceResults()) if (!r.isOnline()) count++;
        int index = 0;
        for (CheckResult r : snapshot.getDeviceResults()) {
            if (r.isOnline()) continue;
            Device d = r.getDevice();
            json.append("    {");
            json.append("\"name\": \"").append(escape(d.getName())).append("\", ");
            json.append("\"ip\": \"").append(escape(d.getIp())).append("\", ");
            json.append("\"area\": \"").append(escape(d.getArea())).append("\", ");
            json.append("\"checkTime\": \"").append(r.getCheckTime() == null ? "" : FMT.format(r.getCheckTime())).append("\"");
            json.append("}").append(index == count - 1 ? "\n" : ",\n");
            index++;
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    public String heartbeat() {
        return "{"
                + "\"event\":\"netpatrol_heartbeat\","
                + "\"status\":\"alive\","
                + "\"time\":\"" + java.time.LocalDateTime.now().format(FMT) + "\""
                + "}";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
