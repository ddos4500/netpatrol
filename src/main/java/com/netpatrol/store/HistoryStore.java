package com.netpatrol.store;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HistoryStore {
    private final File root;
    private final ConfigStore configStore;

    public HistoryStore(ConfigStore configStore) {
        this.configStore = configStore;
        this.root = new File(configStore.getDataDir(), "history");
        if (!root.exists()) root.mkdirs();
    }

    public synchronized File save(String json) throws IOException {
        String day = LocalDate.now().toString();
        File dir = new File(root, day);
        if (!dir.exists()) dir.mkdirs();
        String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
        File file = new File(dir, name);
        write(file, json);
        write(new File(root, "latest.json"), json);
        configStore.saveHistoryJson(json);
        return file;
    }

    public synchronized String latestJson() {
        File latest = new File(root, "latest.json");
        if (!latest.exists()) return "{\n  \"message\": \"no inspection result yet\"\n}\n";
        try {
            return read(latest);
        } catch (IOException e) {
            return "{\n  \"error\": \"" + e.getMessage() + "\"\n}\n";
        }
    }

    private void write(File file, String text) throws IOException {
        OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            out.write(text);
        } finally {
            out.close();
        }
    }

    private String read(File file) throws IOException {
        InputStreamReader in = new InputStreamReader(new FileInputStream(file), "UTF-8");
        try {
            StringBuilder s = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = in.read(buf)) >= 0) s.append(buf, 0, n);
            return s.toString();
        } finally {
            in.close();
        }
    }
}
