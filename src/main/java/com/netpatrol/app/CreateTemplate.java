package com.netpatrol.app;

import com.netpatrol.importer.TemplateXlsxWriter;

import java.io.File;

public class CreateTemplate {
    public static void main(String[] args) throws Exception {
        File file = args.length > 0 ? new File(args[0]) : new File("设备清单模板.xlsx");
        new TemplateXlsxWriter().write(file);
        System.out.println(file.getAbsolutePath());
    }
}
