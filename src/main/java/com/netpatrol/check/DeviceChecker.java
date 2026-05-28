package com.netpatrol.check;

import com.netpatrol.model.CheckResult;
import com.netpatrol.model.Device;

import java.io.OutputStream;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

public class DeviceChecker {
    private static final int TIMEOUT_MS = 3000;
    private static final Pattern TTL_REPLY = Pattern.compile("(?i)\\bTTL\\s*=");

    public CheckResult check(Device device) {
        long start = System.currentTimeMillis();
        try {
            String method = normalize(device.getCheckMethod());
            boolean online;
            if ("PING".equals(method)) {
                online = ping(device.getIp());
            } else if ("PING_TCP".equals(method)) {
                online = ping(device.getIp()) && tcp(device.getIp(), defaultPort(device, 80));
            } else if ("TCP".equals(method)) {
                online = tcp(device.getIp(), defaultPort(device, 80));
            } else if ("HTTP".equals(method)) {
                online = http(device);
            } else if ("ONVIF".equals(method)) {
                online = onvif(device);
            } else if ("SNMP".equals(method)) {
                online = snmpProbe(device);
            } else {
                return result(device, false, start, "不支持的检测方式：" + device.getCheckMethod());
            }
            return result(device, online, start, online ? "" : "检测无响应或返回异常");
        } catch (Exception e) {
            return result(device, false, start, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private CheckResult result(Device device, boolean online, long start, String reason) {
        return new CheckResult(device, online, System.currentTimeMillis() - start, reason, LocalDateTime.now());
    }

    private String normalize(String method) {
        return method == null ? "PING" : method.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private boolean ping(String ip) throws Exception {
        Process process = new ProcessBuilder("ping", "-n", "1", "-w", String.valueOf(TIMEOUT_MS), ip)
                .redirectErrorStream(true)
                .start();
        String output = readProcessOutput(process);
        process.waitFor();
        return TTL_REPLY.matcher(output).find();
    }

    private boolean tcp(String ip, int port) {
        try {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(InetAddress.getByName(ip), port), TIMEOUT_MS);
                return true;
            } finally {
                socket.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean http(Device device) throws Exception {
        String url = device.getHttpUrl();
        if (isBlank(url)) {
            url = "http://" + device.getIp() + ":" + defaultPort(device, 80) + "/";
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        return code >= 200 && code < 500;
    }

    private boolean onvif(Device device) throws Exception {
        int port = device.getOnvifPort() > 0 ? device.getOnvifPort() : defaultPort(device, 80);
        String url = "http://" + device.getIp() + ":" + port + "/onvif/device_service";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<s:Body><GetSystemDateAndTime xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/></s:Body>"
                + "</s:Envelope>";
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        int code = connection.getResponseCode();
        return code >= 200 && code < 500;
    }

    private boolean snmpProbe(Device device) {
        int port = device.getPort() > 0 ? device.getPort() : 161;
        byte[] packet = SnmpPacketBuilder.v2cGet(isBlank(device.getSnmpCommunity()) ? "public" : device.getSnmpCommunity());
        try {
            DatagramSocket socket = new DatagramSocket();
            try {
                socket.setSoTimeout(TIMEOUT_MS);
                InetAddress address = InetAddress.getByName(device.getIp());
                socket.send(new DatagramPacket(packet, packet.length, address, port));
                byte[] buffer = new byte[2048];
                socket.receive(new DatagramPacket(buffer, buffer.length));
                return true;
            } finally {
                socket.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int defaultPort(Device device, int fallback) {
        return device.getPort() > 0 ? device.getPort() : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String readProcessOutput(Process process) throws Exception {
        java.io.InputStream in = process.getInputStream();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            String text = out.toString("GBK");
            if (text.indexOf('\uFFFD') >= 0) text = out.toString("UTF-8");
            return text;
        } finally {
            in.close();
        }
    }
}
