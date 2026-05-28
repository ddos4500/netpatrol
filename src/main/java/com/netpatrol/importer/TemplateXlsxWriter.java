package com.netpatrol.importer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TemplateXlsxWriter {
    private static final String[] HEADERS = {
            "设备名称", "设备类型", "IP地址", "所属区域", "检测方式", "检测端口",
            "SNMP版本", "SNMP团体字", "HTTP地址", "ONVIF端口", "用户名", "密码", "备注"
    };

    private static final String[][] EXAMPLES = {
            {"核心交换机01", "交换机", "192.168.1.1", "机房", "PING_TCP", "22", "2c", "public", "", "", "", "", "示例"},
            {"录像机01", "录像机", "192.168.1.20", "监控中心", "HTTP", "80", "", "", "http://192.168.1.20/", "", "admin", "", "密码可选"},
            {"摄像头01", "摄像头", "192.168.1.101", "一楼大厅", "ONVIF", "", "", "", "", "80", "admin", "", "ONVIF基础探测"}
    };

    public void write(File file) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "</Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"设备清单\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "</Relationships>");
            entry(zip, "xl/worksheets/sheet1.xml", sheetXml());
        } finally {
            zip.close();
        }
    }

    private String sheetXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        row(xml, 1, HEADERS);
        for (int i = 0; i < EXAMPLES.length; i++) {
            row(xml, i + 2, EXAMPLES[i]);
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private void row(StringBuilder xml, int rowNumber, String[] values) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        for (int i = 0; i < values.length; i++) {
            xml.append("<c r=\"").append(columnName(i)).append(rowNumber).append("\" t=\"inlineStr\"><is><t>");
            xml.append(escape(values[i]));
            xml.append("</t></is></c>");
        }
        xml.append("</row>");
    }

    private String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index + 1;
        while (value > 0) {
            int mod = (value - 1) % 26;
            name.insert(0, (char) ('A' + mod));
            value = (value - mod - 1) / 26;
        }
        return name.toString();
    }

    private void entry(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
