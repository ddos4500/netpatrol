package com.netpatrol.snmp;

import com.netpatrol.model.PortCheckResult;
import com.netpatrol.model.PortMonitor;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SnmpPortSpeedChecker {
    private static final String IF_DESCR = "1.3.6.1.2.1.2.2.1.2";
    private static final String IF_SPEED = "1.3.6.1.2.1.2.2.1.5";
    private static final String IF_NAME = "1.3.6.1.2.1.31.1.1.1.1";
    private static final String IF_HIGH_SPEED = "1.3.6.1.2.1.31.1.1.1.15";

    public PortCheckResult check(PortMonitor monitor) {
        Snmp snmp = null;
        try {
            snmp = new Snmp(new DefaultUdpTransportMapping());
            snmp.listen();
            Target target = target(monitor);
            Map<Integer, String> names = walkText(snmp, target, new OID(IF_NAME));
            Map<Integer, String> descrs = walkText(snmp, target, new OID(IF_DESCR));
            Integer ifIndex = findIndex(monitor, names, descrs);
            if (ifIndex == null) {
                return fail(monitor, "未找到匹配端口：" + monitor.getMatchMode() + "=" + monitor.getPortKey());
            }
            long speedMbps = readSpeedMbps(snmp, target, ifIndex);
            boolean alert = speedMbps == monitor.getAlertSpeedMbps();
            String ifName = value(names.get(ifIndex), "-");
            String ifDescr = value(descrs.get(ifIndex), "-");
            String msg = alert ? "端口速率等于告警值 " + monitor.getAlertSpeedMbps() + "Mbps" : "端口速率正常";
            return new PortCheckResult(monitor, true, alert, ifIndex, ifName, ifDescr, speedMbps, msg, LocalDateTime.now());
        } catch (Exception e) {
            return fail(monitor, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (snmp != null) {
                try {
                    snmp.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Target target(PortMonitor monitor) {
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(isBlank(monitor.getSnmpCommunity()) ? "public" : monitor.getSnmpCommunity()));
        target.setAddress(GenericAddress.parse("udp:" + monitor.getSwitchIp() + "/161"));
        target.setVersion("1".equals(monitor.getSnmpVersion()) ? SnmpConstants.version1 : SnmpConstants.version2c);
        target.setRetries(1);
        target.setTimeout(3000);
        return target;
    }

    private Map<Integer, String> walkText(Snmp snmp, Target target, OID root) throws Exception {
        Map<Integer, String> values = new HashMap<Integer, String>();
        OID next = root;
        while (true) {
            PDU pdu = new PDU();
            pdu.setType(PDU.GETNEXT);
            pdu.add(new VariableBinding(next));
            ResponseEvent event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null || event.getResponse().size() == 0) {
                break;
            }
            VariableBinding vb = event.getResponse().get(0);
            OID oid = vb.getOid();
            if (oid == null || !oid.startsWith(root)) {
                break;
            }
            int ifIndex = oid.last();
            values.put(ifIndex, vb.getVariable() == null ? "" : vb.getVariable().toString());
            next = oid;
        }
        return values;
    }

    private Integer findIndex(PortMonitor monitor, Map<Integer, String> names, Map<Integer, String> descrs) {
        String mode = isBlank(monitor.getMatchMode()) ? "ifName" : monitor.getMatchMode().trim();
        String key = value(monitor.getPortKey(), "").trim();
        if ("ifIndex".equalsIgnoreCase(mode)) {
            try {
                return Integer.parseInt(key);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Map<Integer, String> map = "ifDescr".equalsIgnoreCase(mode) ? descrs : names;
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(value(entry.getValue(), "").trim())) {
                return entry.getKey();
            }
        }
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            if (value(entry.getValue(), "").toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private long readSpeedMbps(Snmp snmp, Target target, int ifIndex) throws Exception {
        Variable highSpeed = get(snmp, target, new OID(IF_HIGH_SPEED + "." + ifIndex));
        long high = toLong(highSpeed);
        if (high > 0) {
            return high;
        }
        Variable speed = get(snmp, target, new OID(IF_SPEED + "." + ifIndex));
        long bps = toLong(speed);
        return bps <= 0 ? -1 : bps / 1000 / 1000;
    }

    private Variable get(Snmp snmp, Target target, OID oid) throws Exception {
        PDU pdu = new PDU();
        pdu.setType(PDU.GET);
        pdu.add(new VariableBinding(oid));
        ResponseEvent event = snmp.send(pdu, target);
        if (event == null || event.getResponse() == null || event.getResponse().size() == 0) {
            return null;
        }
        return event.getResponse().get(0).getVariable();
    }

    private long toLong(Variable variable) {
        if (variable == null || variable instanceof Null) {
            return -1;
        }
        try {
            return variable.toLong();
        } catch (Exception e) {
            try {
                return Long.parseLong(variable.toString());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
    }

    private PortCheckResult fail(PortMonitor monitor, String message) {
        return new PortCheckResult(monitor, false, false, -1, "", "", -1, message, LocalDateTime.now());
    }

    private String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
