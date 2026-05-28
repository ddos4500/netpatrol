package com.netpatrol.app;

import com.netpatrol.check.DeviceChecker;
import com.netpatrol.importer.TemplateXlsxWriter;
import com.netpatrol.importer.XlsxDeviceImporter;
import com.netpatrol.model.*;
import com.netpatrol.mqtt.MqttAlertPublisher;
import com.netpatrol.mqtt.MqttPayloadBuilder;
import com.netpatrol.report.JsonReportBuilder;
import com.netpatrol.report.PdfReportWriter;
import com.netpatrol.report.ReportWebServer;
import com.netpatrol.scheduler.DailyTaskScheduler;
import com.netpatrol.snmp.SnmpOidChecker;
import com.netpatrol.snmp.SnmpPortSpeedChecker;
import com.netpatrol.store.ConfigStore;
import com.netpatrol.store.HistoryStore;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class NetPatrolApp extends JFrame {
    private final DefaultTableModel deviceTableModel = new DefaultTableModel(new Object[]{"设备名称", "设备类型", "IP地址", "所属区域", "检测方式", "检测端口", "在线状态", "响应时间(ms)", "失败原因", "巡检时间", "备注"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel monitorTableModel = new DefaultTableModel(new Object[]{"交换机名称", "IP地址", "区域", "SNMP版本", "团体字", "匹配方式", "端口标识", "告警速率Mbps", "备注"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel portResultTableModel = new DefaultTableModel(new Object[]{"交换机", "IP地址", "区域", "端口", "ifIndex", "速率Mbps", "状态", "消息", "检测时间"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel snmpOidTableModel = new DefaultTableModel(new Object[]{"名称", "IP地址", "区域", "SNMP版本", "团体字", "OID", "备注"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel snmpOidResultTableModel = new DefaultTableModel(new Object[]{"名称", "IP地址", "区域", "OID", "值", "类型", "状态", "消息", "检测时间"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel taskTableModel = new DefaultTableModel(new Object[]{"启用", "运行中", "类型", "任务名称", "每天执行时间", "结束时间", "间隔分钟", "设备巡检", "端口监测", "SNMP OID"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel mqttRuleTableModel = new DefaultTableModel(new Object[]{"启用", "规则名称", "消息类型", "Topic"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };

    private final JTable deviceTable = table(deviceTableModel);
    private final JTable monitorTable = table(monitorTableModel);
    private final JTable snmpOidTable = table(snmpOidTableModel);
    private final JTable taskTable = table(taskTableModel);
    private final JTable mqttRuleTable = table(mqttRuleTableModel);
    private final JLabel statusLabel = new JLabel("正在初始化...");
    private final JButton inspectButton = new JButton("开始设备巡检");
    private final JButton exportButton = new JButton("导出PDF报告");
    private final JTextField mqttHostField = new JTextField(14);
    private final JTextField mqttPortField = new JTextField("1883", 5);
    private final JTextField mqttUserField = new JTextField(10);
    private final JPasswordField mqttPasswordField = new JPasswordField(10);
    private final JTextField mqttTopicField = new JTextField(18);
    private final JTextField mqttHeartbeatTopicField = new JTextField(18);
    private final JRadioButton sqliteRadio = new JRadioButton("SQLite");
    private final JRadioButton mysqlRadio = new JRadioButton("MySQL");
    private final JTextField mysqlHostField = new JTextField("127.0.0.1", 12);
    private final JTextField mysqlPortField = new JTextField("3306", 5);
    private final JTextField mysqlDatabaseField = new JTextField("netpatrol", 10);
    private final JTextField mysqlUserField = new JTextField("root", 10);
    private final JPasswordField mysqlPasswordField = new JPasswordField(10);
    private final JTextArea webHint = new JTextArea();

    private final ExecutorService executor = Executors.newFixedThreadPool(16);
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConfigStore configStore = new ConfigStore();
    private final HistoryStore historyStore = new HistoryStore(configStore);
    private final JsonReportBuilder jsonReportBuilder = new JsonReportBuilder();
    private final MqttPayloadBuilder mqttPayloadBuilder = new MqttPayloadBuilder();
    private final DailyTaskScheduler scheduler = new DailyTaskScheduler();
    private final AppConfig config;
    private ReportWebServer webServer;

    private List<Device> devices = new ArrayList<Device>();
    private List<CheckResult> results = new ArrayList<CheckResult>();
    private List<PortCheckResult> portResults = new ArrayList<PortCheckResult>();
    private List<SnmpOidResult> snmpOidResults = new ArrayList<SnmpOidResult>();
    private String latestJson = "{\n  \"message\": \"no inspection result yet\"\n}\n";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new NetPatrolApp().setVisible(true);
            }
        });
    }

    public NetPatrolApp() {
        super("网络设备自动巡检工具");
        this.config = configStore.load();
        this.devices = new ArrayList<Device>(config.getDevices());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1380, 840);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("设备巡检", buildDevicePanel());
        tabs.addTab("端口监测", buildPortPanel());
        tabs.addTab("SNMP OID", buildSnmpOidPanel());
        tabs.addTab("定时任务", buildTaskPanel());
        tabs.addTab("MQTT配置", buildMqttPanel());
        tabs.addTab("MQTT主题规则", buildMqttRulePanel());
        tabs.addTab("数据库配置", buildDatabasePanel());
        tabs.addTab("JSON报告服务", buildWebPanel());
        add(tabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadConfigToUi();
        startWebServer();
        startHeartbeat();
        installTrayBehavior();
        scheduler.start(config.getScheduledTasks(), new DailyTaskScheduler.TaskRunner() {
            public void runScheduledTask(ScheduledTask task) {
                runScheduledInspection(task, false);
            }
        });
        statusLabel.setText("就绪。数据库：" + configStore.getStorageDescription() + "；JSON报告：" + (webServer == null ? "未启动" : webServer.url()));
    }

    private JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        return table;
    }

    private JPanel buildDevicePanel() {
        JButton addButton = new JButton("添加设备");
        JButton editButton = new JButton("编辑设备");
        JButton deleteButton = new JButton("删除设备");
        JButton importButton = new JButton("导入设备清单");
        JButton templateButton = new JButton("导出Excel模板");
        JButton saveButton = new JButton("保存设备");
        JButton exportJsonButton = new JButton("导出JSON");
        JButton exportHtmlButton = new JButton("导出HTML");
        JButton exportCsvButton = new JButton("导出CSV");
        addButton.addActionListener(e -> addDevice());
        editButton.addActionListener(e -> editDevice());
        deleteButton.addActionListener(e -> deleteDevice());
        importButton.addActionListener(e -> importDevices());
        templateButton.addActionListener(e -> exportTemplate());
        saveButton.addActionListener(e -> saveConfig());
        inspectButton.addActionListener(e -> inspectDevices());
        exportButton.addActionListener(e -> exportReport());
        exportJsonButton.addActionListener(e -> exportJsonReport());
        exportHtmlButton.addActionListener(e -> exportHtmlReport());
        exportCsvButton.addActionListener(e -> exportCsvReport());
        exportButton.setEnabled(false);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(importButton);
        toolbar.add(templateButton);
        toolbar.add(saveButton);
        toolbar.add(inspectButton);
        toolbar.add(exportButton);
        toolbar.add(exportJsonButton);
        toolbar.add(exportHtmlButton);
        toolbar.add(exportCsvButton);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(deviceTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPortPanel() {
        JButton addButton = new JButton("添加端口");
        JButton editButton = new JButton("编辑端口");
        JButton deleteButton = new JButton("删除端口");
        JButton saveButton = new JButton("保存配置");
        JButton checkButton = new JButton("立即检测端口");
        addButton.addActionListener(e -> addMonitor());
        editButton.addActionListener(e -> editMonitor());
        deleteButton.addActionListener(e -> deleteMonitor());
        saveButton.addActionListener(e -> saveConfig());
        checkButton.addActionListener(e -> runPortMonitorOnly("手动端口检测", false));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(saveButton);
        toolbar.add(checkButton);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(monitorTable), new JScrollPane(table(portResultTableModel)));
        split.setResizeWeight(0.45);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSnmpOidPanel() {
        JButton addButton = new JButton("添加OID");
        JButton editButton = new JButton("编辑OID");
        JButton deleteButton = new JButton("删除OID");
        JButton saveButton = new JButton("保存配置");
        JButton checkButton = new JButton("立即读取OID");
        addButton.addActionListener(e -> addSnmpOidMonitor());
        editButton.addActionListener(e -> editSnmpOidMonitor());
        deleteButton.addActionListener(e -> deleteSnmpOidMonitor());
        saveButton.addActionListener(e -> saveConfig());
        checkButton.addActionListener(e -> runSnmpOidOnly("手动SNMP OID读取"));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(saveButton);
        toolbar.add(checkButton);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(snmpOidTable), new JScrollPane(table(snmpOidResultTableModel)));
        split.setResizeWeight(0.45);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildTaskPanel() {
        JButton addButton = new JButton("添加任务");
        JButton editButton = new JButton("编辑任务");
        JButton deleteButton = new JButton("删除任务");
        JButton startButton = new JButton("启动循环任务");
        JButton stopButton = new JButton("停止循环任务");
        JButton saveButton = new JButton("保存配置");
        addButton.addActionListener(e -> addTask());
        editButton.addActionListener(e -> editTask());
        deleteButton.addActionListener(e -> deleteTask());
        startButton.addActionListener(e -> startSelectedIntervalTask());
        stopButton.addActionListener(e -> stopSelectedIntervalTask());
        saveButton.addActionListener(e -> saveConfig());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(startButton);
        toolbar.add(stopButton);
        toolbar.add(saveButton);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(taskTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMqttPanel() {
        JButton saveButton = new JButton("保存MQTT配置");
        saveButton.addActionListener(e -> saveConfig());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("服务器"));
        form.add(mqttHostField);
        form.add(new JLabel("端口"));
        form.add(mqttPortField);
        form.add(new JLabel("用户名"));
        form.add(mqttUserField);
        form.add(new JLabel("密码"));
        form.add(mqttPasswordField);
        form.add(new JLabel("告警/汇总Topic"));
        form.add(mqttTopicField);
        form.add(new JLabel("心跳Topic"));
        form.add(mqttHeartbeatTopicField);
        form.add(saveButton);
        JTextArea hint = new JTextArea("掉百兆告警照旧发送。固定时间任务完成后发送JSON汇总；循环任务启动后按间隔一直运行，直到手动停止或退出程序。心跳每10分钟发送一次JSON。");
        hint.setEditable(false);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(form, BorderLayout.NORTH);
        panel.add(hint, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMqttRulePanel() {
        JButton addButton = new JButton("添加规则");
        JButton editButton = new JButton("编辑规则");
        JButton deleteButton = new JButton("删除规则");
        JButton saveButton = new JButton("保存规则");
        addButton.addActionListener(e -> addMqttRule());
        editButton.addActionListener(e -> editMqttRule());
        deleteButton.addActionListener(e -> deleteMqttRule());
        saveButton.addActionListener(e -> saveConfig());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(addButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(saveButton);
        JTextArea hint = new JTextArea("消息类型：FULL_JSON=完整JSON报告，DEVICE_SUMMARY=deviceSummary字段，OFFLINE_DEVICES=离线设备简化数组，PORT_ALERT=掉百兆告警，HEARTBEAT=心跳。允许多个规则使用同一消息类型。");
        hint.setEditable(false);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(mqttRuleTable), BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildDatabasePanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(sqliteRadio);
        group.add(mysqlRadio);
        JButton saveButton = new JButton("保存数据库配置");
        saveButton.addActionListener(e -> saveConfig());
        JPanel form = grid();
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(sqliteRadio);
        typePanel.add(mysqlRadio);
        form.add(new JLabel("存储后端")); form.add(typePanel);
        form.add(new JLabel("MySQL主机")); form.add(mysqlHostField);
        form.add(new JLabel("MySQL端口")); form.add(mysqlPortField);
        form.add(new JLabel("数据库名")); form.add(mysqlDatabaseField);
        form.add(new JLabel("用户名")); form.add(mysqlUserField);
        form.add(new JLabel("密码")); form.add(mysqlPasswordField);
        form.add(new JLabel("操作")); form.add(saveButton);
        JTextArea hint = new JTextArea("SQLite数据库位于主程序目录 data\\netpatrol.db。选择MySQL后，程序会把配置和历史JSON写入MySQL；如果连接失败，会自动回退到SQLite，避免程序无法启动。");
        hint.setEditable(false);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(form, BorderLayout.NORTH);
        panel.add(hint, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildWebPanel() {
        JButton openButton = new JButton("打开JSON报告网页");
        openButton.addActionListener(e -> openWebReport());
        webHint.setEditable(false);
        webHint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(openButton);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(webHint), BorderLayout.CENTER);
        return panel;
    }

    private void loadConfigToUi() {
        MqttConfig mqtt = config.getMqttConfig();
        mqttHostField.setText(value(mqtt.getHost(), ""));
        mqttPortField.setText(String.valueOf(mqtt.getPort()));
        mqttUserField.setText(value(mqtt.getUsername(), ""));
        mqttPasswordField.setText(value(mqtt.getPassword(), ""));
        mqttTopicField.setText(value(mqtt.getTopic(), ""));
        mqttHeartbeatTopicField.setText(value(mqtt.getHeartbeatTopic(), ""));
        DatabaseConfig db = config.getDatabaseConfig();
        if (db.isMysql()) mysqlRadio.setSelected(true); else sqliteRadio.setSelected(true);
        mysqlHostField.setText(value(db.getHost(), "127.0.0.1"));
        mysqlPortField.setText(String.valueOf(db.getPort()));
        mysqlDatabaseField.setText(value(db.getDatabase(), "netpatrol"));
        mysqlUserField.setText(value(db.getUsername(), "root"));
        mysqlPasswordField.setText(value(db.getPassword(), ""));
        latestJson = historyStore.latestJson();
        refreshDeviceTable();
        refreshMonitorTable();
        refreshSnmpOidTable();
        refreshTaskTable();
        refreshMqttRuleTable();
    }

    private void saveConfig() {
        try {
            syncDevicesToConfig();
            MqttConfig mqtt = config.getMqttConfig();
            mqtt.setHost(mqttHostField.getText().trim());
            mqtt.setPort(parseInt(mqttPortField.getText(), 1883));
            mqtt.setUsername(mqttUserField.getText().trim());
            mqtt.setPassword(new String(mqttPasswordField.getPassword()));
            mqtt.setTopic(mqttTopicField.getText().trim());
            mqtt.setHeartbeatTopic(mqttHeartbeatTopicField.getText().trim());
            DatabaseConfig db = config.getDatabaseConfig();
            db.setBackend(mysqlRadio.isSelected() ? "MYSQL" : "SQLITE");
            db.setHost(mysqlHostField.getText().trim());
            db.setPort(parseInt(mysqlPortField.getText(), 3306));
            db.setDatabase(mysqlDatabaseField.getText().trim());
            db.setUsername(mysqlUserField.getText().trim());
            db.setPassword(new String(mysqlPasswordField.getPassword()));
            configStore.save(config);
            statusLabel.setText("配置已保存到：" + configStore.getStorageDescription());
        } catch (Exception ex) {
            showError("保存配置失败", ex);
        }
    }

    private void syncDevicesToConfig() {
        config.getDevices().clear();
        config.getDevices().addAll(devices);
    }

    private void startWebServer() {
        for (int port = 8765; port <= 8775; port++) {
            try {
                webServer = new ReportWebServer(historyStore);
                webServer.start(port);
                webHint.setText("内置JSON报告服务已启动：\n\n" + webServer.url()
                        + "\n\n内网访问示例：http://本机内网IP:" + webServer.getPort() + "/\n"
                        + "\n接口：\n" + webServer.url() + "latest\n" + webServer.url() + "api/latest\n\n服务已监听所有网卡。如果内网IP无法访问，请检查Windows防火墙是否放行端口 " + webServer.getPort() + "。\n网页每5秒刷新一次最新JSON。\n历史记录保存在当前数据库，并按日期归档到主程序目录 data/history。");
                return;
            } catch (Exception ignored) {
                webServer = null;
            }
        }
        webHint.setText("内置JSON报告服务启动失败：8765-8775端口均不可用。");
    }

    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    MqttAlertPublisher publisher = new MqttAlertPublisher();
                    int count = publishToRules(publisher, MqttTopicRule.HEARTBEAT, mqttPayloadBuilder.heartbeat());
                    if (count == 0 && !isBlank(config.getMqttConfig().getHeartbeatTopic())) {
                        publisher.publishHeartbeat(config.getMqttConfig());
                    }
                } catch (Exception ignored) {
                }
            }
        }, 10, 10, TimeUnit.MINUTES);
    }

    private void addDevice() {
        Device d = editDeviceDialog(null);
        if (d != null) {
            devices.add(d);
            refreshDeviceTable();
            saveConfig();
        }
    }

    private void editDevice() {
        int row = selectedRow(deviceTable);
        if (row < 0) return;
        Device updated = editDeviceDialog(devices.get(row));
        if (updated != null) {
            devices.set(row, updated);
            refreshDeviceTable();
            saveConfig();
        }
    }

    private void deleteDevice() {
        int[] rows = selectedRows(deviceTable);
        if (rows.length == 0) return;
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除选中的 " + rows.length + " 台设备吗？", "确认删除", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        for (int i = rows.length - 1; i >= 0; i--) {
            devices.remove(rows[i]);
        }
        refreshDeviceTable();
        saveConfig();
        statusLabel.setText("已删除 " + rows.length + " 台设备");
    }

    private Device editDeviceDialog(Device source) {
        JTextField name = new JTextField(source == null ? "" : source.getName(), 16);
        JComboBox<String> type = new JComboBox<String>(new String[]{"交换机", "录像机", "摄像头", "其他"});
        JTextField ip = new JTextField(source == null ? "" : source.getIp(), 14);
        JTextField area = new JTextField(source == null ? "" : source.getArea(), 12);
        JComboBox<String> method = new JComboBox<String>(new String[]{"PING", "PING_TCP", "TCP", "HTTP", "ONVIF", "SNMP"});
        JTextField port = new JTextField(source == null ? "" : String.valueOf(source.getPort()), 6);
        JTextField snmpVersion = new JTextField(source == null ? "2c" : value(source.getSnmpVersion(), "2c"), 6);
        JTextField snmpCommunity = new JTextField(source == null ? "public" : value(source.getSnmpCommunity(), "public"), 10);
        JTextField httpUrl = new JTextField(source == null ? "" : source.getHttpUrl(), 18);
        JTextField onvifPort = new JTextField(source == null ? "" : String.valueOf(source.getOnvifPort()), 6);
        JTextField username = new JTextField(source == null ? "" : source.getUsername(), 10);
        JPasswordField password = new JPasswordField(source == null ? "" : source.getPassword(), 10);
        JTextField remark = new JTextField(source == null ? "" : source.getRemark(), 18);
        if (source != null) {
            type.setSelectedItem(value(source.getType(), "其他"));
            method.setSelectedItem(value(source.getCheckMethod(), "PING"));
        }
        JPanel panel = grid();
        panel.add(new JLabel("设备名称")); panel.add(name);
        panel.add(new JLabel("设备类型")); panel.add(type);
        panel.add(new JLabel("IP地址")); panel.add(ip);
        panel.add(new JLabel("所属区域")); panel.add(area);
        panel.add(new JLabel("检测方式")); panel.add(method);
        panel.add(new JLabel("检测端口")); panel.add(port);
        panel.add(new JLabel("SNMP版本")); panel.add(snmpVersion);
        panel.add(new JLabel("SNMP团体字")); panel.add(snmpCommunity);
        panel.add(new JLabel("HTTP地址")); panel.add(httpUrl);
        panel.add(new JLabel("ONVIF端口")); panel.add(onvifPort);
        panel.add(new JLabel("用户名")); panel.add(username);
        panel.add(new JLabel("密码")); panel.add(password);
        panel.add(new JLabel("备注")); panel.add(remark);
        if (JOptionPane.showConfirmDialog(this, panel, "设备配置", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        if (name.getText().trim().isEmpty() || ip.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "设备名称和IP地址不能为空", "校验失败", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Device d = new Device();
        d.setName(name.getText().trim());
        d.setType(String.valueOf(type.getSelectedItem()));
        d.setIp(ip.getText().trim());
        d.setArea(area.getText().trim());
        d.setCheckMethod(String.valueOf(method.getSelectedItem()));
        d.setPort(parseInt(port.getText(), 0));
        d.setSnmpVersion(snmpVersion.getText().trim());
        d.setSnmpCommunity(snmpCommunity.getText().trim());
        d.setHttpUrl(httpUrl.getText().trim());
        d.setOnvifPort(parseInt(onvifPort.getText(), 0));
        d.setUsername(username.getText().trim());
        d.setPassword(new String(password.getPassword()));
        d.setRemark(remark.getText().trim());
        return d;
    }

    private void importDevices() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            List<Device> imported = new XlsxDeviceImporter().importFile(chooser.getSelectedFile());
            Object[] options = {"追加", "覆盖", "取消"};
            int choice = JOptionPane.showOptionDialog(this, "请选择导入方式", "导入设备清单", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;
            if (choice == 1) devices.clear();
            devices.addAll(imported);
            results = new ArrayList<CheckResult>();
            refreshDeviceTable();
            saveConfig();
            statusLabel.setText("已导入 " + imported.size() + " 台设备，当前共 " + devices.size() + " 台");
        } catch (Exception ex) {
            showError("导入失败", ex);
        }
    }

    private void inspectDevices() {
        if (devices.isEmpty()) return;
        inspectButton.setEnabled(false);
        statusLabel.setText("正在巡检设备...");
        deviceTableModel.setRowCount(0);
        results = new ArrayList<CheckResult>();
        SwingWorker<Void, CheckResult> worker = new SwingWorker<Void, CheckResult>() {
            protected Void doInBackground() throws Exception {
                final DeviceChecker checker = new DeviceChecker();
                final CountDownLatch latch = new CountDownLatch(devices.size());
                for (final Device device : devices) {
                    executor.submit(new Runnable() {
                        public void run() {
                            try {
                                publish(checker.check(device));
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }
                latch.await();
                return null;
            }

            protected void process(List<CheckResult> chunks) {
                for (CheckResult result : chunks) {
                    results.add(result);
                    addDeviceResultRow(result);
                }
            }

            protected void done() {
                inspectButton.setEnabled(true);
                exportButton.setEnabled(hasAnyResult());
                saveManualSnapshot("手动设备巡检");
                statusLabel.setText("设备巡检完成：总数 " + results.size());
            }
        };
        worker.execute();
    }

    private void runScheduledInspection(final ScheduledTask task, final boolean publishSummary) {
        saveConfig();
        executor.submit(new Runnable() {
            public void run() {
                InspectionSnapshot snapshot = executeInspection(task.getName(), task.isIntervalTask() ? "interval" : "daily", task.isIncludeDeviceCheck(), task.isIncludePortCheck(), task.isIncludeSnmpOidCheck(), true);
                String json = jsonReportBuilder.build(snapshot);
                publishSummaryJson(snapshot, json);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        refreshAllResultTables();
                        refreshTaskTable();
                        statusLabel.setText(task.getName() + " 执行完成，JSON报告已更新");
                    }
                });
            }
        });
    }

    private InspectionSnapshot executeInspection(String taskName, String triggerType, boolean includeDevices, boolean includePorts, boolean includeSnmpOid, boolean publishPortAlerts) {
        InspectionSnapshot snapshot = new InspectionSnapshot();
        snapshot.setTaskName(taskName);
        snapshot.setTriggerType(triggerType);
        snapshot.setStartTime(LocalDateTime.now());
        if (includeDevices) {
            DeviceChecker checker = new DeviceChecker();
            for (Device device : devices) snapshot.getDeviceResults().add(checker.check(device));
        }
        if (includePorts) {
            SnmpPortSpeedChecker checker = new SnmpPortSpeedChecker();
            MqttAlertPublisher publisher = new MqttAlertPublisher();
            for (PortMonitor monitor : config.getPortMonitors()) {
                PortCheckResult result = checker.check(monitor);
                snapshot.getPortResults().add(result);
                if (publishPortAlerts && result.isAlert()) {
                    try {
                        publishPortAlert(publisher, result, taskName);
                        snapshot.getMqttMessages().add("端口告警MQTT已发送：" + monitor.getSwitchIp() + " " + result.getIfName());
                    } catch (Exception e) {
                        snapshot.getMqttMessages().add("端口告警MQTT发送失败：" + e.getMessage());
                    }
                }
            }
        }
        if (includeSnmpOid) {
            SnmpOidChecker checker = new SnmpOidChecker();
            for (SnmpOidMonitor monitor : config.getSnmpOidMonitors()) {
                snapshot.getSnmpOidResults().add(checker.check(monitor));
            }
        }
        snapshot.setEndTime(LocalDateTime.now());
        results = new ArrayList<CheckResult>(snapshot.getDeviceResults());
        portResults = new ArrayList<PortCheckResult>(snapshot.getPortResults());
        snmpOidResults = new ArrayList<SnmpOidResult>(snapshot.getSnmpOidResults());
        return snapshot;
    }

    private void publishSummaryJson(InspectionSnapshot snapshot, String json) {
        try {
            publishInspectionMessages(snapshot, json);
            snapshot.getMqttMessages().add("巡检汇总MQTT已发送");
        } catch (Exception e) {
            snapshot.getMqttMessages().add("巡检汇总MQTT发送失败：" + e.getMessage());
        }
        String updated = jsonReportBuilder.build(snapshot);
        persistSnapshot(snapshot, updated);
        latestJson = updated;
    }

    private void publishInspectionMessages(InspectionSnapshot snapshot, String fullJson) throws Exception {
        MqttAlertPublisher publisher = new MqttAlertPublisher();
        int sent = 0;
        Exception firstError = null;
        try {
            int fullCount = publishToRules(publisher, MqttTopicRule.FULL_JSON, fullJson);
            if (fullCount == 0 && !isBlank(config.getMqttConfig().getTopic())) {
                publisher.publishInspectionSummary(config.getMqttConfig(), fullJson);
                fullCount++;
            }
            sent += fullCount;
        } catch (Exception e) {
            firstError = e;
        }
        try {
            sent += publishToRules(publisher, MqttTopicRule.DEVICE_SUMMARY, mqttPayloadBuilder.deviceSummary(snapshot));
        } catch (Exception e) {
            if (firstError == null) firstError = e;
        }
        try {
            sent += publishToRules(publisher, MqttTopicRule.OFFLINE_DEVICES, mqttPayloadBuilder.offlineDevices(snapshot));
        } catch (Exception e) {
            if (firstError == null) firstError = e;
        }
        if (sent == 0 && firstError != null) throw firstError;
    }

    private void publishPortAlert(MqttAlertPublisher publisher, PortCheckResult result, String taskName) throws Exception {
        String payload = publisher.buildPortSpeedAlertJson(result, taskName);
        int count = publishToRules(publisher, MqttTopicRule.PORT_ALERT, payload);
        if (count == 0) {
            publisher.publishPortSpeedAlert(config.getMqttConfig(), result, taskName);
        }
    }

    private int publishToRules(MqttAlertPublisher publisher, String messageType, String payload) throws Exception {
        int count = 0;
        for (MqttTopicRule rule : config.getMqttTopicRules()) {
            if (!rule.isEnabled()) continue;
            if (!messageType.equalsIgnoreCase(value(rule.getMessageType(), ""))) continue;
            if (isBlank(rule.getTopic())) continue;
            publisher.publishJson(config.getMqttConfig(), rule.getTopic(), payload);
            count++;
        }
        return count;
    }

    private void publishIntervalSummary(final ScheduledTask task) {
        executor.submit(new Runnable() {
            public void run() {
                try {
                    new MqttAlertPublisher().publishInspectionSummary(config.getMqttConfig(), latestJson);
                    SwingUtilities.invokeLater(() -> statusLabel.setText(task.getName() + " 已结束，汇总JSON已通过MQTT发送"));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText(task.getName() + " 已结束，汇总MQTT发送失败：" + e.getMessage()));
                }
            }
        });
    }

    private void runPortMonitorOnly(final String taskName, final boolean publishMqtt) {
        executor.submit(new Runnable() {
            public void run() {
                InspectionSnapshot snapshot = executeInspection(taskName, "manual", false, true, false, publishMqtt);
                latestJson = jsonReportBuilder.build(snapshot);
                persistSnapshot(snapshot, latestJson);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        refreshPortResults();
                        exportButton.setEnabled(hasAnyResult());
                        statusLabel.setText(taskName + "完成：端口数 " + portResults.size() + "，告警 " + countAlerts(portResults));
                    }
                });
            }
        });
    }

    private void runSnmpOidOnly(final String taskName) {
        executor.submit(new Runnable() {
            public void run() {
                InspectionSnapshot snapshot = executeInspection(taskName, "manual", false, false, true, false);
                latestJson = jsonReportBuilder.build(snapshot);
                persistSnapshot(snapshot, latestJson);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        refreshSnmpOidResults();
                        exportButton.setEnabled(hasAnyResult());
                        statusLabel.setText(taskName + "完成：OID数 " + snmpOidResults.size());
                    }
                });
            }
        });
    }

    private void saveManualSnapshot(String taskName) {
        InspectionSnapshot snapshot = new InspectionSnapshot();
        snapshot.setTaskName(taskName);
        snapshot.setTriggerType("manual");
        snapshot.setStartTime(LocalDateTime.now());
        snapshot.getDeviceResults().addAll(results);
        snapshot.getPortResults().addAll(portResults);
        snapshot.getSnmpOidResults().addAll(snmpOidResults);
        snapshot.setEndTime(LocalDateTime.now());
        latestJson = jsonReportBuilder.build(snapshot);
        persistSnapshot(snapshot, latestJson);
    }

    private void persistSnapshot(InspectionSnapshot snapshot, String json) {
        try {
            historyStore.save(json);
        } catch (Exception e) {
            snapshot.getMqttMessages().add("JSON文件/兼容历史保存失败：" + e.getMessage());
        }
        try {
            configStore.saveInspectionSnapshot(snapshot, json);
        } catch (Exception e) {
            snapshot.getMqttMessages().add("分字段巡检结果保存失败：" + e.getMessage());
        }
    }

    private void exportTemplate() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("设备清单模板.xlsx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = ensureExt(chooser.getSelectedFile(), ".xlsx");
        try {
            new TemplateXlsxWriter().write(file);
            statusLabel.setText("Excel模板已导出：" + file.getAbsolutePath());
        } catch (Exception ex) {
            showError("模板导出失败", ex);
        }
    }

    private void exportReport() {
        if (!hasAnyResult()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("巡检报告.pdf"));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File file = ensureExt(chooser.getSelectedFile(), ".pdf");
        final List<CheckResult> resultSnapshot = new ArrayList<CheckResult>(results);
        final List<PortCheckResult> portSnapshot = new ArrayList<PortCheckResult>(portResults);
        exportButton.setEnabled(false);
        statusLabel.setText("正在后台导出PDF报告，请稍候...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                new PdfReportWriter().write(file, resultSnapshot, portSnapshot);
                return null;
            }

            protected void done() {
                exportButton.setEnabled(hasAnyResult());
                try {
                    get();
                    statusLabel.setText("PDF报告已导出：" + file.getAbsolutePath());
                    JOptionPane.showMessageDialog(NetPatrolApp.this, "PDF报告已导出：\n" + file.getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showError("导出失败", ex);
                }
            }
        };
        worker.execute();
    }

    private void exportJsonReport() {
        if (!hasAnyResult()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("巡检报告.json"));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件 (*.json)", "json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File file = ensureExt(chooser.getSelectedFile(), ".json");
        final String json = currentSnapshotJson("手动导出JSON", "export-json");
        runTextExport(file, json, "JSON报告");
    }

    private void exportHtmlReport() {
        if (!hasAnyResult()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("巡检报告.html"));
        chooser.setFileFilter(new FileNameExtensionFilter("HTML 文件 (*.html)", "html"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File file = ensureExt(chooser.getSelectedFile(), ".html");
        final String json = currentSnapshotJson("手动导出HTML", "export-html");
        final String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>巡检报告</title>"
                + "<style>body{margin:0;background:#0f172a;color:#e2e8f0;font:14px Consolas,'Microsoft YaHei',monospace;}pre{white-space:pre-wrap;margin:0;padding:20px;}</style>"
                + "</head><body><pre>" + escapeHtml(json) + "</pre></body></html>";
        runTextExport(file, html, "HTML报告");
    }

    private void exportCsvReport() {
        if (!hasAnyResult()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("巡检明细.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File file = ensureExt(chooser.getSelectedFile(), ".csv");
        final String csv = currentCsv();
        runTextExport(file, csv, "CSV明细");
    }

    private void runTextExport(final File file, final String text, final String name) {
        statusLabel.setText("正在导出" + name + "...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                writeUtf8(file, text);
                return null;
            }

            protected void done() {
                try {
                    get();
                    statusLabel.setText(name + "已导出：" + file.getAbsolutePath());
                    JOptionPane.showMessageDialog(NetPatrolApp.this, name + "已导出：\n" + file.getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showError("导出失败", ex);
                }
            }
        };
        worker.execute();
    }

    private String currentSnapshotJson(String taskName, String triggerType) {
        InspectionSnapshot snapshot = new InspectionSnapshot();
        snapshot.setTaskName(taskName);
        snapshot.setTriggerType(triggerType);
        snapshot.setStartTime(LocalDateTime.now());
        snapshot.getDeviceResults().addAll(results);
        snapshot.getPortResults().addAll(portResults);
        snapshot.getSnmpOidResults().addAll(snmpOidResults);
        snapshot.setEndTime(LocalDateTime.now());
        return jsonReportBuilder.build(snapshot);
    }

    private String currentCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("类别,名称,类型/IP,区域,检测方式/端口,状态,响应/速率,时间,说明\n");
        for (CheckResult r : results) {
            Device d = r.getDevice();
            csv.append("设备,").append(csv(d.getName())).append(",").append(csv(d.getType() + "/" + d.getIp())).append(",")
                    .append(csv(d.getArea())).append(",").append(csv(d.getCheckMethod())).append(",")
                    .append(csv(r.isOnline() ? "在线" : "离线")).append(",").append(r.getResponseTimeMs()).append("ms,")
                    .append(csv(r.getCheckTime().toString().replace('T', ' '))).append(",").append(csv(r.getFailureReason())).append("\n");
        }
        for (PortCheckResult r : portResults) {
            PortMonitor m = r.getMonitor();
            csv.append("端口,").append(csv(m.getSwitchName())).append(",").append(csv(m.getSwitchIp())).append(",")
                    .append(csv(m.getArea())).append(",").append(csv(value(r.getIfName(), m.getPortKey()))).append(",")
                    .append(csv(r.isAlert() ? "掉百兆告警" : r.isSuccess() ? "正常" : "失败")).append(",")
                    .append(r.getSpeedMbps()).append("Mbps,").append(csv(r.getCheckTime().toString().replace('T', ' '))).append(",")
                    .append(csv(r.getMessage())).append("\n");
        }
        for (SnmpOidResult r : snmpOidResults) {
            SnmpOidMonitor m = r.getMonitor();
            csv.append("SNMP OID,").append(csv(m.getName())).append(",").append(csv(m.getTargetIp())).append(",")
                    .append(csv(m.getArea())).append(",").append(csv(m.getOid())).append(",")
                    .append(csv(r.isSuccess() ? "成功" : "失败")).append(",").append(csv(r.getValue())).append(",")
                    .append(csv(r.getCheckTime().toString().replace('T', ' '))).append(",").append(csv(r.getMessage())).append("\n");
        }
        return csv.toString();
    }

    private void writeUtf8(File file, String text) throws Exception {
        OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            out.write(text);
        } finally {
            out.close();
        }
    }

    private String csv(String text) {
        String value = text == null ? "" : text;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void addMonitor() {
        PortMonitor monitor = editMonitorDialog(null);
        if (monitor != null) {
            config.getPortMonitors().add(monitor);
            refreshMonitorTable();
            saveConfig();
        }
    }

    private void editMonitor() {
        int row = selectedRow(monitorTable);
        if (row < 0) return;
        PortMonitor updated = editMonitorDialog(config.getPortMonitors().get(row));
        if (updated != null) {
            config.getPortMonitors().set(row, updated);
            refreshMonitorTable();
            saveConfig();
        }
    }

    private void deleteMonitor() {
        int row = selectedRow(monitorTable);
        if (row < 0) return;
        config.getPortMonitors().remove(row);
        refreshMonitorTable();
        saveConfig();
    }

    private PortMonitor editMonitorDialog(PortMonitor source) {
        JTextField name = new JTextField(source == null ? "" : source.getSwitchName(), 18);
        JTextField ip = new JTextField(source == null ? "" : source.getSwitchIp(), 14);
        JTextField area = new JTextField(source == null ? "" : source.getArea(), 12);
        JComboBox<String> version = new JComboBox<String>(new String[]{"2c", "1"});
        JComboBox<String> mode = new JComboBox<String>(new String[]{"ifName", "ifDescr", "ifIndex"});
        JTextField community = new JTextField(source == null ? "public" : source.getSnmpCommunity(), 10);
        JTextField portKey = new JTextField(source == null ? "" : source.getPortKey(), 16);
        JTextField alertSpeed = new JTextField(source == null ? "100" : String.valueOf(source.getAlertSpeedMbps()), 6);
        JTextField remark = new JTextField(source == null ? "" : source.getRemark(), 18);
        if (source != null) {
            version.setSelectedItem(source.getSnmpVersion());
            mode.setSelectedItem(source.getMatchMode());
        }
        JPanel panel = grid();
        panel.add(new JLabel("交换机名称")); panel.add(name);
        panel.add(new JLabel("IP地址")); panel.add(ip);
        panel.add(new JLabel("区域")); panel.add(area);
        panel.add(new JLabel("SNMP版本")); panel.add(version);
        panel.add(new JLabel("团体字")); panel.add(community);
        panel.add(new JLabel("匹配方式")); panel.add(mode);
        panel.add(new JLabel("端口标识")); panel.add(portKey);
        panel.add(new JLabel("告警速率Mbps")); panel.add(alertSpeed);
        panel.add(new JLabel("备注")); panel.add(remark);
        if (JOptionPane.showConfirmDialog(this, panel, "端口监测配置", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        if (ip.getText().trim().isEmpty() || portKey.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "IP地址和端口标识不能为空", "校验失败", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        PortMonitor monitor = new PortMonitor();
        monitor.setSwitchName(name.getText().trim());
        monitor.setSwitchIp(ip.getText().trim());
        monitor.setArea(area.getText().trim());
        monitor.setSnmpVersion(String.valueOf(version.getSelectedItem()));
        monitor.setSnmpCommunity(community.getText().trim());
        monitor.setMatchMode(String.valueOf(mode.getSelectedItem()));
        monitor.setPortKey(portKey.getText().trim());
        monitor.setAlertSpeedMbps(parseInt(alertSpeed.getText(), 100));
        monitor.setRemark(remark.getText().trim());
        return monitor;
    }

    private void addSnmpOidMonitor() {
        SnmpOidMonitor monitor = editSnmpOidDialog(null);
        if (monitor != null) {
            config.getSnmpOidMonitors().add(monitor);
            refreshSnmpOidTable();
            saveConfig();
        }
    }

    private void editSnmpOidMonitor() {
        int row = selectedRow(snmpOidTable);
        if (row < 0) return;
        SnmpOidMonitor updated = editSnmpOidDialog(config.getSnmpOidMonitors().get(row));
        if (updated != null) {
            config.getSnmpOidMonitors().set(row, updated);
            refreshSnmpOidTable();
            saveConfig();
        }
    }

    private void deleteSnmpOidMonitor() {
        int[] rows = selectedRows(snmpOidTable);
        if (rows.length == 0) return;
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除选中的 " + rows.length + " 条OID监测配置吗？", "确认删除", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        for (int i = rows.length - 1; i >= 0; i--) {
            config.getSnmpOidMonitors().remove(rows[i]);
        }
        refreshSnmpOidTable();
        saveConfig();
    }

    private SnmpOidMonitor editSnmpOidDialog(SnmpOidMonitor source) {
        JTextField name = new JTextField(source == null ? "" : source.getName(), 18);
        JTextField ip = new JTextField(source == null ? "" : source.getTargetIp(), 14);
        JTextField area = new JTextField(source == null ? "" : source.getArea(), 12);
        JComboBox<String> version = new JComboBox<String>(new String[]{"2c", "1"});
        JTextField community = new JTextField(source == null ? "public" : value(source.getSnmpCommunity(), "public"), 10);
        JTextField oid = new JTextField(source == null ? "" : source.getOid(), 24);
        JTextField remark = new JTextField(source == null ? "" : source.getRemark(), 18);
        if (source != null) version.setSelectedItem(value(source.getSnmpVersion(), "2c"));
        JPanel panel = grid();
        panel.add(new JLabel("名称")); panel.add(name);
        panel.add(new JLabel("IP地址")); panel.add(ip);
        panel.add(new JLabel("区域")); panel.add(area);
        panel.add(new JLabel("SNMP版本")); panel.add(version);
        panel.add(new JLabel("团体字")); panel.add(community);
        panel.add(new JLabel("OID")); panel.add(oid);
        panel.add(new JLabel("备注")); panel.add(remark);
        if (JOptionPane.showConfirmDialog(this, panel, "SNMP OID配置", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        if (ip.getText().trim().isEmpty() || oid.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "IP地址和OID不能为空", "校验失败", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        SnmpOidMonitor monitor = new SnmpOidMonitor();
        monitor.setName(name.getText().trim());
        monitor.setTargetIp(ip.getText().trim());
        monitor.setArea(area.getText().trim());
        monitor.setSnmpVersion(String.valueOf(version.getSelectedItem()));
        monitor.setSnmpCommunity(community.getText().trim());
        monitor.setOid(oid.getText().trim());
        monitor.setRemark(remark.getText().trim());
        return monitor;
    }

    private void addMqttRule() {
        MqttTopicRule rule = editMqttRuleDialog(null);
        if (rule != null) {
            config.getMqttTopicRules().add(rule);
            refreshMqttRuleTable();
            saveConfig();
        }
    }

    private void editMqttRule() {
        int row = selectedRow(mqttRuleTable);
        if (row < 0) return;
        MqttTopicRule updated = editMqttRuleDialog(config.getMqttTopicRules().get(row));
        if (updated != null) {
            config.getMqttTopicRules().set(row, updated);
            refreshMqttRuleTable();
            saveConfig();
        }
    }

    private void deleteMqttRule() {
        int[] rows = selectedRows(mqttRuleTable);
        if (rows.length == 0) return;
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除选中的 " + rows.length + " 条MQTT主题规则吗？", "确认删除", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        for (int i = rows.length - 1; i >= 0; i--) {
            config.getMqttTopicRules().remove(rows[i]);
        }
        refreshMqttRuleTable();
        saveConfig();
    }

    private MqttTopicRule editMqttRuleDialog(MqttTopicRule source) {
        JTextField name = new JTextField(source == null ? "" : source.getName(), 16);
        JComboBox<String> type = new JComboBox<String>(new String[]{MqttTopicRule.FULL_JSON, MqttTopicRule.DEVICE_SUMMARY, MqttTopicRule.OFFLINE_DEVICES, MqttTopicRule.PORT_ALERT, MqttTopicRule.HEARTBEAT});
        JTextField topic = new JTextField(source == null ? "" : source.getTopic(), 24);
        JCheckBox enabled = new JCheckBox("启用", source == null || source.isEnabled());
        if (source != null) type.setSelectedItem(value(source.getMessageType(), MqttTopicRule.FULL_JSON));
        JPanel panel = grid();
        panel.add(new JLabel("规则名称")); panel.add(name);
        panel.add(new JLabel("消息类型")); panel.add(type);
        panel.add(new JLabel("Topic")); panel.add(topic);
        panel.add(new JLabel("状态")); panel.add(enabled);
        if (JOptionPane.showConfirmDialog(this, panel, "MQTT主题规则", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        if (topic.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Topic不能为空", "校验失败", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        MqttTopicRule rule = new MqttTopicRule();
        rule.setName(name.getText().trim());
        rule.setMessageType(String.valueOf(type.getSelectedItem()));
        rule.setTopic(topic.getText().trim());
        rule.setEnabled(enabled.isSelected());
        return rule;
    }

    private void addTask() {
        ScheduledTask task = editTaskDialog(null);
        if (task != null) {
            config.getScheduledTasks().add(task);
            refreshTaskTable();
            saveConfig();
        }
    }

    private void editTask() {
        int row = selectedRow(taskTable);
        if (row < 0) return;
        ScheduledTask updated = editTaskDialog(config.getScheduledTasks().get(row));
        if (updated != null) {
            config.getScheduledTasks().set(row, updated);
            refreshTaskTable();
            saveConfig();
        }
    }

    private void deleteTask() {
        int row = selectedRow(taskTable);
        if (row < 0) return;
        config.getScheduledTasks().remove(row);
        refreshTaskTable();
        saveConfig();
    }

    private ScheduledTask editTaskDialog(ScheduledTask source) {
        JTextField name = new JTextField(source == null ? "巡检任务" : source.getName(), 16);
        JComboBox<String> type = new JComboBox<String>(new String[]{"DAILY", "INTERVAL"});
        JTextField runTime = new JTextField(source == null || source.getRunTime() == null ? "09:00" : source.getRunTime().toString(), 8);
        JTextField interval = new JTextField(source == null ? "5" : String.valueOf(source.getIntervalMinutes()), 5);
        JCheckBox device = new JCheckBox("设备在线巡检", source != null && source.isIncludeDeviceCheck());
        JCheckBox port = new JCheckBox("端口速率监测", source == null || source.isIncludePortCheck());
        JCheckBox snmpOid = new JCheckBox("自定义SNMP OID", source != null && source.isIncludeSnmpOidCheck());
        JCheckBox enabled = new JCheckBox("启用", source == null || source.isEnabled());
        if (source != null) type.setSelectedItem(source.getTaskType());
        JPanel panel = grid();
        panel.add(new JLabel("任务名称")); panel.add(name);
        panel.add(new JLabel("任务类型")); panel.add(type);
        panel.add(new JLabel("固定执行时间(HH:mm)")); panel.add(runTime);
        panel.add(new JLabel("循环间隔分钟")); panel.add(interval);
        panel.add(new JLabel("巡检内容")); panel.add(device);
        panel.add(new JLabel("巡检内容")); panel.add(port);
        panel.add(new JLabel("巡检内容")); panel.add(snmpOid);
        panel.add(new JLabel("状态")); panel.add(enabled);
        if (JOptionPane.showConfirmDialog(this, panel, "定时任务", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        ScheduledTask task = new ScheduledTask();
        task.setName(name.getText().trim());
        task.setTaskType(String.valueOf(type.getSelectedItem()));
        task.setIntervalMinutes(Math.max(1, parseInt(interval.getText(), 5)));
        task.setIncludeDeviceCheck(device.isSelected());
        task.setIncludePortCheck(port.isSelected());
        task.setIncludeSnmpOidCheck(snmpOid.isSelected());
        task.setEnabled(enabled.isSelected());
        try {
            if (!task.isIntervalTask()) task.setRunTime(LocalTime.parse(runTime.getText().trim()));
            if (task.isIntervalTask()) task.setEndTime(null);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "时间格式应为 HH:mm，例如 09:30", "校验失败", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return task;
    }

    private void startSelectedIntervalTask() {
        int row = selectedRow(taskTable);
        if (row < 0) return;
        ScheduledTask task = config.getScheduledTasks().get(row);
        if (!task.isIntervalTask()) {
            JOptionPane.showMessageDialog(this, "请选择 INTERVAL 类型任务", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        task.setRunning(true);
        task.setNextRunTime(LocalTime.now());
        refreshTaskTable();
        saveConfig();
        statusLabel.setText("循环任务已启动：" + task.getName());
    }

    private void stopSelectedIntervalTask() {
        int row = selectedRow(taskTable);
        if (row < 0) return;
        ScheduledTask task = config.getScheduledTasks().get(row);
        task.setRunning(false);
        task.setNextRunTime(null);
        refreshTaskTable();
        statusLabel.setText("循环任务已停止：" + task.getName());
    }

    private void refreshAllResultTables() {
        refreshDeviceTableWithResults();
        refreshPortResults();
        refreshSnmpOidResults();
        exportButton.setEnabled(hasAnyResult());
    }

    private void refreshDeviceTable() {
        deviceTableModel.setRowCount(0);
        for (Device d : devices) {
            deviceTableModel.addRow(new Object[]{d.getName(), d.getType(), d.getIp(), d.getArea(), d.getCheckMethod(), d.getPort(), "未巡检", "", "", "", d.getRemark()});
        }
        inspectButton.setEnabled(!devices.isEmpty());
    }

    private void refreshDeviceTableWithResults() {
        deviceTableModel.setRowCount(0);
        for (CheckResult r : results) addDeviceResultRow(r);
    }

    private void addDeviceResultRow(CheckResult result) {
        Device d = result.getDevice();
        deviceTableModel.addRow(new Object[]{d.getName(), d.getType(), d.getIp(), d.getArea(), d.getCheckMethod(), d.getPort(), result.isOnline() ? "在线" : "离线", result.getResponseTimeMs(), result.getFailureReason(), result.getCheckTime().toString().replace('T', ' '), d.getRemark()});
    }

    private void refreshMonitorTable() {
        monitorTableModel.setRowCount(0);
        for (PortMonitor m : config.getPortMonitors()) {
            monitorTableModel.addRow(new Object[]{m.getSwitchName(), m.getSwitchIp(), m.getArea(), m.getSnmpVersion(), m.getSnmpCommunity(), m.getMatchMode(), m.getPortKey(), m.getAlertSpeedMbps(), m.getRemark()});
        }
    }

    private void refreshSnmpOidTable() {
        snmpOidTableModel.setRowCount(0);
        for (SnmpOidMonitor m : config.getSnmpOidMonitors()) {
            snmpOidTableModel.addRow(new Object[]{m.getName(), m.getTargetIp(), m.getArea(), m.getSnmpVersion(), m.getSnmpCommunity(), m.getOid(), m.getRemark()});
        }
    }

    private void refreshMqttRuleTable() {
        mqttRuleTableModel.setRowCount(0);
        for (MqttTopicRule rule : config.getMqttTopicRules()) {
            mqttRuleTableModel.addRow(new Object[]{rule.isEnabled() ? "是" : "否", rule.getName(), rule.getMessageType(), rule.getTopic()});
        }
    }

    private void refreshTaskTable() {
        taskTableModel.setRowCount(0);
        for (ScheduledTask t : config.getScheduledTasks()) {
            taskTableModel.addRow(new Object[]{t.isEnabled() ? "是" : "否", t.isRunning() ? "是" : "否", t.getTaskType(), t.getName(), t.getRunTime(), t.isIntervalTask() ? "" : t.getEndTime(), t.getIntervalMinutes(), t.isIncludeDeviceCheck() ? "是" : "否", t.isIncludePortCheck() ? "是" : "否", t.isIncludeSnmpOidCheck() ? "是" : "否"});
        }
    }

    private void refreshPortResults() {
        portResultTableModel.setRowCount(0);
        for (PortCheckResult r : portResults) {
            PortMonitor m = r.getMonitor();
            portResultTableModel.addRow(new Object[]{m.getSwitchName(), m.getSwitchIp(), m.getArea(), value(r.getIfName(), m.getPortKey()), r.getIfIndex(), r.getSpeedMbps(), r.isAlert() ? "掉百兆告警" : r.isSuccess() ? "正常" : "失败", r.getMessage(), r.getCheckTime().toString().replace('T', ' ')});
        }
    }

    private void refreshSnmpOidResults() {
        snmpOidResultTableModel.setRowCount(0);
        for (SnmpOidResult r : snmpOidResults) {
            SnmpOidMonitor m = r.getMonitor();
            snmpOidResultTableModel.addRow(new Object[]{m.getName(), m.getTargetIp(), m.getArea(), m.getOid(), r.getValue(), r.getVariableType(), r.isSuccess() ? "成功" : "失败", r.getMessage(), r.getCheckTime().toString().replace('T', ' ')});
        }
    }

    private boolean hasAnyResult() {
        return !results.isEmpty() || !portResults.isEmpty() || !snmpOidResults.isEmpty();
    }

    private JPanel grid() {
        return new JPanel(new GridLayout(0, 2, 6, 6));
    }

    private int selectedRow(JTable table) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行", "提示", JOptionPane.INFORMATION_MESSAGE);
            return -1;
        }
        return table.convertRowIndexToModel(viewRow);
    }

    private int[] selectedRows(JTable table) {
        int[] viewRows = table.getSelectedRows();
        if (viewRows.length == 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行或多行", "提示", JOptionPane.INFORMATION_MESSAGE);
            return new int[0];
        }
        int[] modelRows = new int[viewRows.length];
        for (int i = 0; i < viewRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(viewRows[i]);
        }
        java.util.Arrays.sort(modelRows);
        return modelRows;
    }

    private int countAlerts(List<PortCheckResult> list) {
        int count = 0;
        for (PortCheckResult r : list) if (r.isAlert()) count++;
        return count;
    }

    private void openWebReport() {
        try {
            if (webServer != null && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(webServer.url()));
        } catch (Exception ex) {
            showError("打开网页失败", ex);
        }
    }

    private void installTrayBehavior() {
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                saveConfig();
                if (SystemTray.isSupported()) {
                    setVisible(false);
                    statusLabel.setText("程序已隐藏到系统托盘，定时任务继续运行");
                } else {
                    shutdown();
                }
            }
        });
        if (!SystemTray.isSupported()) return;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem("打开");
            MenuItem exit = new MenuItem("退出");
            show.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
            });
            exit.addActionListener(e -> shutdown());
            menu.add(show);
            menu.add(exit);
            TrayIcon icon = new TrayIcon(trayImage(), "网络设备自动巡检工具", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> setVisible(true));
            SystemTray.getSystemTray().add(icon);
        } catch (Exception ignored) {
        }
    }

    private Image trayImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(37, 99, 235));
            g.fillOval(1, 1, 14, 14);
            g.setColor(Color.WHITE);
            g.drawLine(4, 8, 7, 11);
            g.drawLine(7, 11, 12, 5);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void shutdown() {
        try { saveConfig(); } catch (Exception ignored) {}
        if (webServer != null) webServer.stop();
        scheduler.stop();
        heartbeatScheduler.shutdownNow();
        MqttAlertPublisher.closeSharedClient();
        executor.shutdownNow();
        for (Window window : Window.getWindows()) window.dispose();
        System.exit(0);
    }

    private File ensureExt(File file, String ext) {
        return file.getName().toLowerCase().endsWith(ext) ? file : new File(file.getParentFile(), file.getName() + ext);
    }

    private int parseInt(String text, int fallback) {
        try {
            return text == null || text.trim().isEmpty() ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private void showError(String title, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        statusLabel.setText(title + "：" + ex.getMessage());
    }
}
