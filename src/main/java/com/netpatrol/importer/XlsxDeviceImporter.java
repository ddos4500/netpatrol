package com.netpatrol.importer;

import com.netpatrol.model.Device;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.*;

public class XlsxDeviceImporter {
    public List<Device> importFile(File file) throws Exception {
        ZipFile zip = new ZipFile(file);
        try {
            List<String> sharedStrings = readSharedStrings(zip);
            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry == null) {
                throw new IllegalArgumentException("未找到第一个工作表 sheet1.xml");
            }
            List<List<String>> rows = readSheet(zip.getInputStream(sheetEntry), sharedStrings);
            return toDevices(rows);
        } finally {
            zip.close();
        }
    }

    private List<String> readSharedStrings(ZipFile zip) throws Exception {
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        List<String> values = new ArrayList<String>();
        if (entry == null) {
            return values;
        }
        Document doc = parseXml(zip.getInputStream(entry));
        NodeList items = doc.getElementsByTagName("si");
        for (int i = 0; i < items.getLength(); i++) {
            values.add(items.item(i).getTextContent());
        }
        return values;
    }

    private List<List<String>> readSheet(InputStream input, List<String> sharedStrings) throws Exception {
        Document doc = parseXml(input);
        NodeList rowNodes = doc.getElementsByTagName("row");
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cellNodes = row.getElementsByTagName("c");
            List<String> cells = new ArrayList<String>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                int col = columnIndex(cell.getAttribute("r"));
                while (cells.size() <= col) {
                    cells.add("");
                }
                cells.set(col, cellValue(cell, sharedStrings));
            }
            rows.add(cells);
        }
        return rows;
    }

    private Document parseXml(InputStream input) throws Exception {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(input);
        } finally {
            input.close();
        }
    }

    private String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        NodeList inline = cell.getElementsByTagName("t");
        if ("inlineStr".equals(type) && inline.getLength() > 0) {
            return inline.item(0).getTextContent().trim();
        }
        NodeList values = cell.getElementsByTagName("v");
        if (values.getLength() == 0) {
            return "";
        }
        String raw = values.item(0).getTextContent().trim();
        if ("s".equals(type)) {
            int index = parseInt(raw, -1);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index).trim() : "";
        }
        return raw;
    }

    private int columnIndex(String ref) {
        int result = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                result = result * 26 + (c - 'A' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, result - 1);
    }

    private List<Device> toDevices(List<List<String>> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> headers = new HashMap<String, Integer>();
        List<String> first = rows.get(0);
        for (int i = 0; i < first.size(); i++) {
            headers.put(first.get(i).trim(), i);
        }
        List<Device> devices = new ArrayList<Device>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String name = get(row, headers, "设备名称");
            String type = get(row, headers, "设备类型");
            String ip = get(row, headers, "IP地址");
            String method = get(row, headers, "检测方式");
            if (isBlank(name) && isBlank(type) && isBlank(ip) && isBlank(method)) {
                continue;
            }
            if (isBlank(name) || isBlank(type) || isBlank(ip) || isBlank(method)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行缺少必填字段：设备名称、设备类型、IP地址、检测方式");
            }
            Device device = new Device();
            device.setName(name);
            device.setType(type);
            device.setIp(ip);
            device.setArea(get(row, headers, "所属区域"));
            device.setCheckMethod(method.toUpperCase(Locale.ROOT));
            device.setPort(parseInt(get(row, headers, "检测端口"), 0));
            device.setSnmpVersion(get(row, headers, "SNMP版本"));
            device.setSnmpCommunity(get(row, headers, "SNMP团体字"));
            device.setHttpUrl(get(row, headers, "HTTP地址"));
            device.setOnvifPort(parseInt(get(row, headers, "ONVIF端口"), 0));
            device.setUsername(get(row, headers, "用户名"));
            device.setPassword(get(row, headers, "密码"));
            device.setRemark(get(row, headers, "备注"));
            devices.add(device);
        }
        return devices;
    }

    private String get(List<String> row, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private int parseInt(String text, int fallback) {
        try {
            return text == null || text.trim().isEmpty() ? fallback : (int) Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
