package com.netpatrol.snmp;

import com.netpatrol.model.SnmpOidMonitor;
import com.netpatrol.model.SnmpOidResult;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.time.LocalDateTime;

public class SnmpOidChecker {
    public SnmpOidResult check(SnmpOidMonitor monitor) {
        Snmp snmp = null;
        try {
            if (isBlank(monitor.getTargetIp()) || isBlank(monitor.getOid())) {
                return fail(monitor, "IP地址和OID不能为空");
            }
            snmp = new Snmp(new DefaultUdpTransportMapping());
            snmp.listen();
            PDU pdu = new PDU();
            pdu.setType(PDU.GET);
            pdu.add(new VariableBinding(new OID(normalizeOid(monitor.getOid()))));
            ResponseEvent event = snmp.send(pdu, target(monitor));
            if (event == null || event.getResponse() == null || event.getResponse().size() == 0) {
                return fail(monitor, "SNMP无响应");
            }
            VariableBinding vb = event.getResponse().get(0);
            Variable variable = vb.getVariable();
            if (variable == null || variable instanceof Null) {
                return fail(monitor, "OID无返回值");
            }
            return new SnmpOidResult(monitor, true, variable.toString(), variable.getClass().getSimpleName(), "读取成功", LocalDateTime.now());
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

    private Target target(SnmpOidMonitor monitor) {
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(isBlank(monitor.getSnmpCommunity()) ? "public" : monitor.getSnmpCommunity()));
        target.setAddress(GenericAddress.parse("udp:" + monitor.getTargetIp() + "/161"));
        target.setVersion("1".equals(monitor.getSnmpVersion()) ? SnmpConstants.version1 : SnmpConstants.version2c);
        target.setRetries(1);
        target.setTimeout(3000);
        return target;
    }

    private String normalizeOid(String oid) {
        String value = oid == null ? "" : oid.trim();
        return value.startsWith(".") ? value.substring(1) : value;
    }

    private SnmpOidResult fail(SnmpOidMonitor monitor, String message) {
        return new SnmpOidResult(monitor, false, "", "", message, LocalDateTime.now());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
