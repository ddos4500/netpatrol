package com.netpatrol.report;

import com.netpatrol.model.CheckResult;
import com.netpatrol.model.Device;
import com.netpatrol.model.PortCheckResult;
import com.netpatrol.model.PortMonitor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PdfReportWriter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int LEFT = 50;
    private static final int TOP = 790;
    private static final int LINE_HEIGHT = 18;

    public void write(File file, List<CheckResult> results) throws IOException {
        write(file, results, Collections.<PortCheckResult>emptyList());
    }

    public void write(File file, List<CheckResult> results, List<PortCheckResult> portResults) throws IOException {
        List<List<String>> pages = paginate(lines(results, portResults), 38);
        List<byte[]> objects = new ArrayList<byte[]>();
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder("[");
        for (int i = 0; i < pages.size(); i++) {
            kids.append(3 + i * 3).append(" 0 R ");
        }
        kids.append("]");
        objects.add(bytes("<< /Type /Pages /Kids " + kids + " /Count " + pages.size() + " >>"));
        for (int i = 0; i < pages.size(); i++) {
            int contentObject = 4 + i * 3;
            int imageObject = 5 + i * 3;
            byte[] image = renderPage(pages.get(i));
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT + "] /Resources << /XObject << /Im0 " + imageObject + " 0 R >> >> /Contents " + contentObject + " 0 R >>"));
            byte[] stream = bytes("q\n" + PAGE_WIDTH + " 0 0 " + PAGE_HEIGHT + " 0 0 cm\n/Im0 Do\nQ");
            objects.add(bytes("<< /Length " + stream.length + " >>\nstream\n" + new String(stream, "ISO-8859-1") + "\nendstream"));
            objects.add(imageObject(image));
        }
        writePdf(file, objects);
    }

    private List<String> lines(List<CheckResult> results, List<PortCheckResult> portResults) {
        List<String> lines = new ArrayList<String>();
        int online = 0;
        Map<String, int[]> byType = new TreeMap<String, int[]>();
        Map<String, int[]> byArea = new TreeMap<String, int[]>();
        for (CheckResult result : results) {
            if (result.isOnline()) online++;
            addStat(byType, value(result.getDevice().getType(), "未分类"), result.isOnline());
            addStat(byArea, value(result.getDevice().getArea(), "未填写区域"), result.isOnline());
        }
        lines.add("网络设备自动巡检报告");
        lines.add("报告生成时间: " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        lines.add("");
        lines.add("设备在线巡检统计");
        lines.add("总设备数: " + results.size() + "    在线数量: " + online + "    离线数量: " + (results.size() - online) + "    在线率: " + percent(online, results.size()));
        lines.add("");
        lines.add("按设备类型统计");
        appendStats(lines, byType);
        lines.add("");
        lines.add("按区域统计");
        appendStats(lines, byArea);
        lines.add("");
        lines.add("离线设备明细");
        boolean hasOffline = false;
        for (CheckResult result : results) {
            if (!result.isOnline()) {
                hasOffline = true;
                Device d = result.getDevice();
                lines.add(d.getName() + " | " + d.getType() + " | " + d.getIp() + " | " + value(d.getArea(), "-") + " | " + result.getFailureReason());
            }
        }
        if (!hasOffline) {
            lines.add("无离线设备");
        }
        lines.add("");
        lines.add("交换机端口速率监测");
        appendPortSummary(lines, portResults);
        lines.add("");
        lines.add("全部设备巡检明细");
        lines.add("名称 | 类型 | IP | 区域 | 方式 | 状态 | 响应ms | 时间 | 备注");
        for (CheckResult result : results) {
            Device d = result.getDevice();
            lines.add(d.getName() + " | " + d.getType() + " | " + d.getIp() + " | " + value(d.getArea(), "-")
                    + " | " + d.getCheckMethod() + " | " + (result.isOnline() ? "在线" : "离线")
                    + " | " + result.getResponseTimeMs() + " | "
                    + result.getCheckTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " | " + value(d.getRemark(), ""));
        }
        if (!portResults.isEmpty()) {
            lines.add("");
            lines.add("端口速率监测明细");
            lines.add("交换机 | IP | 区域 | 端口 | ifIndex | 速率Mbps | 状态 | 时间 | 说明");
            for (PortCheckResult result : portResults) {
                PortMonitor m = result.getMonitor();
                lines.add(value(m.getSwitchName(), "-") + " | " + m.getSwitchIp() + " | " + value(m.getArea(), "-")
                        + " | " + value(result.getIfName(), m.getPortKey()) + " | " + result.getIfIndex()
                        + " | " + result.getSpeedMbps() + " | " + (result.isAlert() ? "掉百兆告警" : result.isSuccess() ? "正常" : "失败")
                        + " | " + result.getCheckTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        + " | " + value(result.getMessage(), ""));
            }
        }
        return lines;
    }

    private void appendPortSummary(List<String> lines, List<PortCheckResult> portResults) {
        if (portResults.isEmpty()) {
            lines.add("无端口监测结果");
            return;
        }
        int alerts = 0;
        int failed = 0;
        for (PortCheckResult result : portResults) {
            if (result.isAlert()) alerts++;
            if (!result.isSuccess()) failed++;
        }
        lines.add("监测端口数: " + portResults.size() + "    掉百兆告警: " + alerts + "    检测失败: " + failed);
        if (alerts > 0) {
            lines.add("掉百兆端口明细");
            for (PortCheckResult result : portResults) {
                if (result.isAlert()) {
                    PortMonitor m = result.getMonitor();
                    lines.add(value(m.getSwitchName(), "-") + " | " + m.getSwitchIp() + " | " + value(result.getIfName(), m.getPortKey())
                            + " | 当前速率 " + result.getSpeedMbps() + "Mbps");
                }
            }
        }
    }

    private void addStat(Map<String, int[]> map, String key, boolean online) {
        int[] stat = map.get(key);
        if (stat == null) {
            stat = new int[2];
            map.put(key, stat);
        }
        stat[0]++;
        if (online) stat[1]++;
    }

    private void appendStats(List<String> lines, Map<String, int[]> stats) {
        if (stats.isEmpty()) {
            lines.add("无数据");
            return;
        }
        for (Map.Entry<String, int[]> entry : stats.entrySet()) {
            int total = entry.getValue()[0];
            int online = entry.getValue()[1];
            lines.add(entry.getKey() + ": 总数 " + total + ", 在线 " + online + ", 离线 " + (total - online) + ", 在线率 " + percent(online, total));
        }
    }

    private String percent(int count, int total) {
        return total == 0 ? "0%" : String.format(Locale.ROOT, "%.2f%%", count * 100.0 / total);
    }

    private String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text.trim();
    }

    private List<List<String>> paginate(List<String> lines, int pageSize) {
        List<List<String>> pages = new ArrayList<List<String>>();
        for (int i = 0; i < lines.size(); i += pageSize) {
            pages.add(lines.subList(i, Math.min(lines.size(), i + pageSize)));
        }
        if (pages.isEmpty()) {
            pages.add(Collections.singletonList("无巡检结果"));
        }
        return pages;
    }

    private byte[] renderPage(List<String> lines) throws IOException {
        BufferedImage image = new BufferedImage(PAGE_WIDTH * 2, PAGE_HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(30, 41, 59));
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 20));
            int y = (PAGE_HEIGHT - TOP) * 2;
            for (String line : lines) {
                drawClipped(g, line, LEFT * 2, y, (PAGE_WIDTH - LEFT * 2) * 2);
                y += LINE_HEIGHT * 2;
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private void drawClipped(Graphics2D g, String line, int x, int y, int maxWidth) {
        String text = line == null ? "" : line;
        FontMetrics metrics = g.getFontMetrics();
        while (metrics.stringWidth(text) > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 2) + "...";
        }
        g.drawString(text, x, y);
    }

    private byte[] imageObject(byte[] image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("<< /Type /XObject /Subtype /Image /Width " + (PAGE_WIDTH * 2) + " /Height " + (PAGE_HEIGHT * 2) + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + image.length + " >>\nstream\n"));
        out.write(image);
        out.write(bytes("\nendstream"));
        return out.toByteArray();
    }

    private void writePdf(File file, List<byte[]> objects) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<Integer>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.write(bytes((i + 1) + " 0 obj\n"));
            out.write(objects.get(i));
            out.write(bytes("\nendobj\n"));
        }
        int xref = out.size();
        out.write(bytes("xref\n0 " + (objects.size() + 1) + "\n"));
        out.write(bytes("0000000000 65535 f \n"));
        for (Integer offset : offsets) {
            out.write(bytes(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
        }
        out.write(bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF"));
        FileOutputStream fileOut = new FileOutputStream(file);
        try {
            fileOut.write(out.toByteArray());
        } finally {
            fileOut.close();
        }
    }

    private byte[] bytes(String text) {
        try {
            return text.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
