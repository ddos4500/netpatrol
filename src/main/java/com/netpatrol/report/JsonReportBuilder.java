package com.netpatrol.report;

import com.netpatrol.model.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class JsonReportBuilder {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String build(InspectionSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        int online = 0;
        int portAlerts = 0;
        int portFailed = 0;
        int snmpOidFailed = 0;
        for (CheckResult r : snapshot.getDeviceResults()) if (r.isOnline()) online++;
        for (PortCheckResult r : snapshot.getPortResults()) {
            if (r.isAlert()) portAlerts++;
            if (!r.isSuccess()) portFailed++;
        }
        for (SnmpOidResult r : snapshot.getSnmpOidResults()) if (!r.isSuccess()) snmpOidFailed++;
        json.append("{\n");
        field(json, 1, "event", "inspection_completed", true);
        field(json, 1, "taskName", snapshot.getTaskName(), true);
        field(json, 1, "triggerType", snapshot.getTriggerType(), true);
        field(json, 1, "startTime", snapshot.getStartTime() == null ? "" : FMT.format(snapshot.getStartTime()), true);
        field(json, 1, "endTime", snapshot.getEndTime() == null ? "" : FMT.format(snapshot.getEndTime()), true);
        json.append(indent(1)).append("\"deviceSummary\": {\n");
        number(json, 2, "total", snapshot.getDeviceResults().size(), true);
        number(json, 2, "online", online, true);
        number(json, 2, "offline", snapshot.getDeviceResults().size() - online, false);
        json.append(indent(1)).append("},\n");
        json.append(indent(1)).append("\"portSummary\": {\n");
        number(json, 2, "total", snapshot.getPortResults().size(), true);
        number(json, 2, "alerts", portAlerts, true);
        number(json, 2, "failed", portFailed, false);
        json.append(indent(1)).append("},\n");
        json.append(indent(1)).append("\"snmpOidSummary\": {\n");
        number(json, 2, "total", snapshot.getSnmpOidResults().size(), true);
        number(json, 2, "success", snapshot.getSnmpOidResults().size() - snmpOidFailed, true);
        number(json, 2, "failed", snmpOidFailed, false);
        json.append(indent(1)).append("},\n");
        deviceArray(json, "offlineDevices", offline(snapshot.getDeviceResults()), true);
        portArray(json, "portAlerts", alerts(snapshot.getPortResults()), true);
        deviceArray(json, "deviceResults", snapshot.getDeviceResults(), true);
        portArray(json, "portResults", snapshot.getPortResults(), true);
        snmpOidArray(json, "snmpOidResults", snapshot.getSnmpOidResults(), true);
        stringArray(json, "mqttMessages", snapshot.getMqttMessages(), false);
        json.append("}\n");
        return json.toString();
    }

    private List<CheckResult> offline(List<CheckResult> results) {
        java.util.ArrayList<CheckResult> list = new java.util.ArrayList<CheckResult>();
        for (CheckResult r : results) if (!r.isOnline()) list.add(r);
        return list;
    }

    private List<PortCheckResult> alerts(List<PortCheckResult> results) {
        java.util.ArrayList<PortCheckResult> list = new java.util.ArrayList<PortCheckResult>();
        for (PortCheckResult r : results) if (r.isAlert()) list.add(r);
        return list;
    }

    private void deviceArray(StringBuilder json, String name, List<CheckResult> results, boolean comma) {
        json.append(indent(1)).append("\"").append(name).append("\": [\n");
        for (int i = 0; i < results.size(); i++) {
            CheckResult r = results.get(i);
            Device d = r.getDevice();
            json.append(indent(2)).append("{");
            inline(json, "name", d.getName()); json.append(", ");
            inline(json, "type", d.getType()); json.append(", ");
            inline(json, "ip", d.getIp()); json.append(", ");
            inline(json, "area", d.getArea()); json.append(", ");
            inline(json, "method", d.getCheckMethod()); json.append(", ");
            json.append("\"online\": ").append(r.isOnline()).append(", ");
            json.append("\"responseTimeMs\": ").append(r.getResponseTimeMs()).append(", ");
            inline(json, "failureReason", r.getFailureReason()); json.append(", ");
            inline(json, "checkTime", FMT.format(r.getCheckTime()));
            json.append("}").append(i == results.size() - 1 ? "\n" : ",\n");
        }
        json.append(indent(1)).append("]").append(comma ? "," : "").append("\n");
    }

    private void portArray(StringBuilder json, String name, List<PortCheckResult> results, boolean comma) {
        json.append(indent(1)).append("\"").append(name).append("\": [\n");
        for (int i = 0; i < results.size(); i++) {
            PortCheckResult r = results.get(i);
            PortMonitor m = r.getMonitor();
            json.append(indent(2)).append("{");
            inline(json, "switchName", m.getSwitchName()); json.append(", ");
            inline(json, "switchIp", m.getSwitchIp()); json.append(", ");
            inline(json, "area", m.getArea()); json.append(", ");
            inline(json, "portName", r.getIfName()); json.append(", ");
            inline(json, "portDescr", r.getIfDescr()); json.append(", ");
            json.append("\"ifIndex\": ").append(r.getIfIndex()).append(", ");
            json.append("\"currentSpeedMbps\": ").append(r.getSpeedMbps()).append(", ");
            json.append("\"alertSpeedMbps\": ").append(m.getAlertSpeedMbps()).append(", ");
            json.append("\"alert\": ").append(r.isAlert()).append(", ");
            inline(json, "message", r.getMessage()); json.append(", ");
            inline(json, "checkTime", FMT.format(r.getCheckTime()));
            json.append("}").append(i == results.size() - 1 ? "\n" : ",\n");
        }
        json.append(indent(1)).append("]").append(comma ? "," : "").append("\n");
    }

    private void snmpOidArray(StringBuilder json, String name, List<SnmpOidResult> results, boolean comma) {
        json.append(indent(1)).append("\"").append(name).append("\": [\n");
        for (int i = 0; i < results.size(); i++) {
            SnmpOidResult r = results.get(i);
            SnmpOidMonitor m = r.getMonitor();
            json.append(indent(2)).append("{");
            inline(json, "name", m.getName()); json.append(", ");
            inline(json, "targetIp", m.getTargetIp()); json.append(", ");
            inline(json, "area", m.getArea()); json.append(", ");
            inline(json, "oid", m.getOid()); json.append(", ");
            inline(json, "value", r.getValue()); json.append(", ");
            inline(json, "variableType", r.getVariableType()); json.append(", ");
            json.append("\"success\": ").append(r.isSuccess()).append(", ");
            inline(json, "message", r.getMessage()); json.append(", ");
            inline(json, "checkTime", FMT.format(r.getCheckTime()));
            json.append("}").append(i == results.size() - 1 ? "\n" : ",\n");
        }
        json.append(indent(1)).append("]").append(comma ? "," : "").append("\n");
    }

    private void stringArray(StringBuilder json, String name, List<String> values, boolean comma) {
        json.append(indent(1)).append("\"").append(name).append("\": [\n");
        for (int i = 0; i < values.size(); i++) {
            json.append(indent(2)).append("\"").append(escape(values.get(i))).append("\"").append(i == values.size() - 1 ? "\n" : ",\n");
        }
        json.append(indent(1)).append("]").append(comma ? "," : "").append("\n");
    }

    private void field(StringBuilder json, int level, String name, String value, boolean comma) {
        json.append(indent(level)).append("\"").append(name).append("\": \"").append(escape(value)).append("\"").append(comma ? "," : "").append("\n");
    }

    private void number(StringBuilder json, int level, String name, int value, boolean comma) {
        json.append(indent(level)).append("\"").append(name).append("\": ").append(value).append(comma ? "," : "").append("\n");
    }

    private void inline(StringBuilder json, String name, String value) {
        json.append("\"").append(name).append("\": \"").append(escape(value)).append("\"");
    }

    private String indent(int level) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < level; i++) s.append("  ");
        return s.toString();
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
