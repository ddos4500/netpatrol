package com.netpatrol.app;

import com.netpatrol.importer.XlsxDeviceImporter;
import com.netpatrol.model.CheckResult;
import com.netpatrol.model.Device;
import com.netpatrol.report.PdfReportWriter;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SmokeTest {
    public static void main(String[] args) throws Exception {
        File template = new File(args.length > 0 ? args[0] : "设备清单模板.xlsx");
        List<Device> devices = new XlsxDeviceImporter().importFile(template);
        List<CheckResult> results = new ArrayList<CheckResult>();
        for (int i = 0; i < devices.size(); i++) {
            Device device = devices.get(i);
            boolean online = i % 2 == 0;
            results.add(new CheckResult(device, online, 30 + i * 15, online ? "" : "示例离线原因", LocalDateTime.now()));
        }
        File report = new File("冒烟测试巡检报告.pdf");
        new PdfReportWriter().write(report, results);
        System.out.println("导入设备数: " + devices.size());
        System.out.println("示例报告: " + report.getAbsolutePath());
    }
}
