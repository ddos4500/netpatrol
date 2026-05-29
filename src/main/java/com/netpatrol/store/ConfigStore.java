package com.netpatrol.store;

import com.netpatrol.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalTime;
import java.util.Properties;

public class ConfigStore {
    private final File appDir;
    private final File dataDir;
    private final File dbFile;
    private final File storagePropertiesFile;
    private final File oldPropertiesFile;
    private final File oldDbFile;
    private DatabaseConfig databaseConfig;
    private boolean mysqlActive;
    private String activeStorageDescription;
    private String lastStorageError;

    public ConfigStore() {
        this.appDir = detectAppDir();
        this.dataDir = new File(appDir, "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dbFile = new File(dataDir, "netpatrol.db");
        this.storagePropertiesFile = new File(dataDir, "storage.properties");
        File oldDir = new File(System.getProperty("user.home"), ".netpatrol");
        this.oldPropertiesFile = new File(oldDir, "config.properties");
        this.oldDbFile = new File(oldDir, "netpatrol.db");
        migrateOldSqliteIfNeeded();
        this.databaseConfig = loadDatabaseConfig();
        init();
    }

    public AppConfig load() {
        init();
        migrateOldPropertiesIfNeeded();
        AppConfig config = new AppConfig();
        config.setDatabaseConfig(copyDatabaseConfig(databaseConfig));
        try (Connection c = connect()) {
            loadMqtt(c, config.getMqttConfig());
            loadMqttTopicRules(c, config);
            loadDevices(c, config);
            loadPortMonitors(c, config);
            loadSnmpOidMonitors(c, config);
            loadTasks(c, config);
        } catch (Exception ignored) {
        }
        return config;
    }

    public void save(AppConfig config) throws Exception {
        this.databaseConfig = copyDatabaseConfig(config.getDatabaseConfig());
        saveDatabaseConfig(this.databaseConfig);
        init();
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            saveMqtt(c, config.getMqttConfig());
            replaceMqttTopicRules(c, config);
            replaceDevices(c, config);
            replacePortMonitors(c, config);
            replaceSnmpOidMonitors(c, config);
            replaceTasks(c, config);
            c.commit();
        }
    }

    public File getFile() {
        return dbFile;
    }

    public File getDataDir() {
        return dataDir;
    }

    public String getStorageDescription() {
        return activeStorageDescription == null ? dbFile.getAbsolutePath() : activeStorageDescription;
    }

    public String getLastStorageError() {
        return lastStorageError == null ? "" : lastStorageError;
    }

    public void testConnection(DatabaseConfig cfg) throws SQLException {
        DatabaseConfig copy = copyDatabaseConfig(cfg);
        try (Connection c = copy.isMysql() ? connectMysql(copy) : DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement s = c.createStatement()) {
            if (copy.isMysql()) {
                s.executeQuery("select 1").close();
            }
        }
    }

    private Connection connect() throws SQLException {
        if (mysqlActive) {
            return connectMysql(databaseConfig);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private Connection connectMysql(DatabaseConfig cfg) throws SQLException {
        String host = value(cfg.getHost()).trim();
        String database = value(cfg.getDatabase()).trim();
        String url = "jdbc:mysql://" + host + ":" + cfg.getPort() + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true"
                + "&connectTimeout=5000&socketTimeout=30000&tcpKeepAlive=true";
        return DriverManager.getConnection(url, value(cfg.getUsername()).trim(), value(cfg.getPassword()));
    }

    private void init() {
        mysqlActive = false;
        lastStorageError = "";
        activeStorageDescription = "SQLite：" + dbFile.getAbsolutePath();
        if (databaseConfig != null && databaseConfig.isMysql()) {
            mysqlActive = true;
            activeStorageDescription = "MySQL：" + value(databaseConfig.getHost()) + ":" + databaseConfig.getPort() + "/" + value(databaseConfig.getDatabase());
        }
        try (Connection c = connect(); Statement s = c.createStatement()) {
            if (!mysqlActive) activeStorageDescription = "SQLite：" + dbFile.getAbsolutePath();
            createSchema(s, mysqlActive);
        } catch (Exception e) {
            if (databaseConfig != null && databaseConfig.isMysql()) {
                mysqlActive = false;
                lastStorageError = e.getClass().getSimpleName() + ": " + value(e.getMessage());
                activeStorageDescription = "SQLite：" + dbFile.getAbsolutePath() + "（MySQL连接失败，已回退：" + lastStorageError + "）";
                try (Connection c = connect(); Statement s = c.createStatement()) {
                    createSchema(s, false);
                } catch (Exception ignored2) {
                }
            }
        }
    }

    private void createSchema(Statement s, boolean mysql) throws SQLException {
        String id = mysql ? "integer primary key auto_increment" : "integer primary key autoincrement";
        String createdAt = mysql ? "timestamp default current_timestamp" : "text default current_timestamp";
        s.execute("create table if not exists app_kv (`key` varchar(128) primary key, value text)");
        s.execute("create table if not exists devices (id " + id + ", name text, type text, ip text, area text, check_method text, port integer, snmp_version text, snmp_community text, http_url text, onvif_port integer, username text, password text, remark text)");
        s.execute("create table if not exists port_monitors (id " + id + ", switch_name text, switch_ip text, area text, snmp_version text, snmp_community text, match_mode text, port_key text, alert_speed_mbps integer, remark text)");
        s.execute("create table if not exists snmp_oid_monitors (id " + id + ", name text, target_ip text, area text, snmp_version text, snmp_community text, oid text, remark text)");
        s.execute("create table if not exists scheduled_tasks (id " + id + ", name text, type text, run_time text, end_time text, interval_minutes integer, include_device_check integer, include_port_check integer, include_snmp_oid_check integer, enabled integer)");
        s.execute("create table if not exists mqtt_topic_rules (id " + id + ", name text, message_type text, topic text, enabled integer)");
        s.execute("create table if not exists history_json (id " + id + ", created_at " + createdAt + ", task_name text, trigger_type text, json text)");
        s.execute("create table if not exists inspection_runs (id " + id + ", created_at " + createdAt + ", task_name text, trigger_type text, start_time text, end_time text, raw_json text)");
        s.execute("create table if not exists device_check_results (id " + id + ", run_id integer, name text, type text, ip text, area text, check_method text, port integer, online integer, response_time_ms integer, failure_reason text, check_time text, remark text)");
        s.execute("create table if not exists port_check_results (id " + id + ", run_id integer, switch_name text, switch_ip text, area text, port_name text, port_descr text, if_index integer, speed_mbps integer, alert_speed_mbps integer, success integer, alert integer, message text, check_time text)");
        s.execute("create table if not exists snmp_oid_check_results (id " + id + ", run_id integer, name text, target_ip text, area text, oid text, value text, variable_type text, success integer, message text, check_time text)");
        s.execute("create table if not exists inspection_mqtt_messages (id " + id + ", run_id integer, message text)");
        s.execute("create table if not exists inspection_current_summary (id integer primary key, task_name text, trigger_type text, start_time text, end_time text, device_total integer, device_online integer, device_offline integer, port_total integer, port_alerts integer, port_failed integer, snmp_oid_total integer, snmp_oid_success integer, snmp_oid_failed integer, updated_at text, raw_json text)");
        s.execute("create table if not exists device_current_status (id " + id + ", name text, type text, ip text, area text, check_method text, port integer, online integer, response_time_ms integer, failure_reason text, check_time text, remark text, updated_at text)");
        s.execute("create table if not exists port_current_status (id " + id + ", switch_name text, switch_ip text, area text, port_key text, port_name text, port_descr text, if_index integer, speed_mbps integer, alert_speed_mbps integer, success integer, alert integer, message text, check_time text, updated_at text)");
        s.execute("create table if not exists snmp_oid_current_status (id " + id + ", name text, target_ip text, area text, oid text, value text, variable_type text, success integer, message text, check_time text, updated_at text)");
        s.execute("create table if not exists inspection_current_mqtt_messages (id " + id + ", message text, updated_at text)");
        if (mysql) {
            try { s.execute("create index idx_device_current_status_ip on device_current_status(ip(64))"); } catch (Exception ignored) {}
            try { s.execute("create index idx_port_current_status_key on port_current_status(switch_ip(64), port_key(128))"); } catch (Exception ignored) {}
            try { s.execute("create index idx_snmp_oid_current_status_key on snmp_oid_current_status(target_ip(64), oid(255))"); } catch (Exception ignored) {}
        } else {
            s.execute("create index if not exists idx_device_current_status_ip on device_current_status(ip)");
            s.execute("create index if not exists idx_port_current_status_key on port_current_status(switch_ip, port_key)");
            s.execute("create index if not exists idx_snmp_oid_current_status_key on snmp_oid_current_status(target_ip, oid)");
        }
        try { s.execute("alter table scheduled_tasks add column include_snmp_oid_check integer default 0"); } catch (Exception ignored) {}
        try { s.execute("alter table mqtt_topic_rules add column enabled integer default 1"); } catch (Exception ignored) {}
    }

    private void loadMqtt(Connection c, MqttConfig mqtt) throws SQLException {
        mqtt.setHost(kv(c, "mqtt.host", ""));
        mqtt.setPort(intValue(kv(c, "mqtt.port", "1883"), 1883));
        mqtt.setUsername(kv(c, "mqtt.username", ""));
        mqtt.setPassword(kv(c, "mqtt.password", ""));
        mqtt.setTopic(kv(c, "mqtt.topic", ""));
        mqtt.setHeartbeatTopic(kv(c, "mqtt.heartbeatTopic", ""));
    }

    private void saveMqtt(Connection c, MqttConfig mqtt) throws SQLException {
        put(c, "mqtt.host", value(mqtt.getHost()));
        put(c, "mqtt.port", String.valueOf(mqtt.getPort()));
        put(c, "mqtt.username", value(mqtt.getUsername()));
        put(c, "mqtt.password", value(mqtt.getPassword()));
        put(c, "mqtt.topic", value(mqtt.getTopic()));
        put(c, "mqtt.heartbeatTopic", value(mqtt.getHeartbeatTopic()));
    }

    private void loadMqttTopicRules(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select * from mqtt_topic_rules order by id")) {
            while (r.next()) {
                MqttTopicRule rule = new MqttTopicRule();
                rule.setName(r.getString("name"));
                rule.setMessageType(r.getString("message_type"));
                rule.setTopic(r.getString("topic"));
                rule.setEnabled(r.getInt("enabled") == 1);
                config.getMqttTopicRules().add(rule);
            }
        }
    }

    private void replaceMqttTopicRules(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("delete from mqtt_topic_rules");
        }
        String sql = "insert into mqtt_topic_rules(name,message_type,topic,enabled) values(?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (MqttTopicRule rule : config.getMqttTopicRules()) {
                p.setString(1, value(rule.getName()));
                p.setString(2, value(rule.getMessageType()));
                p.setString(3, value(rule.getTopic()));
                p.setInt(4, rule.isEnabled() ? 1 : 0);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void loadDevices(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select * from devices order by id")) {
            while (r.next()) {
                Device d = new Device();
                d.setName(r.getString("name"));
                d.setType(r.getString("type"));
                d.setIp(r.getString("ip"));
                d.setArea(r.getString("area"));
                d.setCheckMethod(r.getString("check_method"));
                d.setPort(r.getInt("port"));
                d.setSnmpVersion(r.getString("snmp_version"));
                d.setSnmpCommunity(r.getString("snmp_community"));
                d.setHttpUrl(r.getString("http_url"));
                d.setOnvifPort(r.getInt("onvif_port"));
                d.setUsername(r.getString("username"));
                d.setPassword(r.getString("password"));
                d.setRemark(r.getString("remark"));
                config.getDevices().add(d);
            }
        }
    }

    private void replaceDevices(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("delete from devices");
        }
        String sql = "insert into devices(name,type,ip,area,check_method,port,snmp_version,snmp_community,http_url,onvif_port,username,password,remark) values(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (Device d : config.getDevices()) {
                p.setString(1, value(d.getName()));
                p.setString(2, value(d.getType()));
                p.setString(3, value(d.getIp()));
                p.setString(4, value(d.getArea()));
                p.setString(5, value(d.getCheckMethod()));
                p.setInt(6, d.getPort());
                p.setString(7, value(d.getSnmpVersion()));
                p.setString(8, value(d.getSnmpCommunity()));
                p.setString(9, value(d.getHttpUrl()));
                p.setInt(10, d.getOnvifPort());
                p.setString(11, value(d.getUsername()));
                p.setString(12, value(d.getPassword()));
                p.setString(13, value(d.getRemark()));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void loadPortMonitors(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select * from port_monitors order by id")) {
            while (r.next()) {
                PortMonitor m = new PortMonitor();
                m.setSwitchName(r.getString("switch_name"));
                m.setSwitchIp(r.getString("switch_ip"));
                m.setArea(r.getString("area"));
                m.setSnmpVersion(r.getString("snmp_version"));
                m.setSnmpCommunity(r.getString("snmp_community"));
                m.setMatchMode(r.getString("match_mode"));
                m.setPortKey(r.getString("port_key"));
                m.setAlertSpeedMbps(r.getInt("alert_speed_mbps"));
                m.setRemark(r.getString("remark"));
                config.getPortMonitors().add(m);
            }
        }
    }

    private void replacePortMonitors(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("delete from port_monitors");
        }
        String sql = "insert into port_monitors(switch_name,switch_ip,area,snmp_version,snmp_community,match_mode,port_key,alert_speed_mbps,remark) values(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (PortMonitor m : config.getPortMonitors()) {
                p.setString(1, value(m.getSwitchName()));
                p.setString(2, value(m.getSwitchIp()));
                p.setString(3, value(m.getArea()));
                p.setString(4, value(m.getSnmpVersion()));
                p.setString(5, value(m.getSnmpCommunity()));
                p.setString(6, value(m.getMatchMode()));
                p.setString(7, value(m.getPortKey()));
                p.setInt(8, m.getAlertSpeedMbps());
                p.setString(9, value(m.getRemark()));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void loadSnmpOidMonitors(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select * from snmp_oid_monitors order by id")) {
            while (r.next()) {
                SnmpOidMonitor m = new SnmpOidMonitor();
                m.setName(r.getString("name"));
                m.setTargetIp(r.getString("target_ip"));
                m.setArea(r.getString("area"));
                m.setSnmpVersion(r.getString("snmp_version"));
                m.setSnmpCommunity(r.getString("snmp_community"));
                m.setOid(r.getString("oid"));
                m.setRemark(r.getString("remark"));
                config.getSnmpOidMonitors().add(m);
            }
        }
    }

    private void replaceSnmpOidMonitors(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("delete from snmp_oid_monitors");
        }
        String sql = "insert into snmp_oid_monitors(name,target_ip,area,snmp_version,snmp_community,oid,remark) values(?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (SnmpOidMonitor m : config.getSnmpOidMonitors()) {
                p.setString(1, value(m.getName()));
                p.setString(2, value(m.getTargetIp()));
                p.setString(3, value(m.getArea()));
                p.setString(4, value(m.getSnmpVersion()));
                p.setString(5, value(m.getSnmpCommunity()));
                p.setString(6, value(m.getOid()));
                p.setString(7, value(m.getRemark()));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void loadTasks(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select * from scheduled_tasks order by id")) {
            while (r.next()) {
                ScheduledTask t = new ScheduledTask();
                t.setName(r.getString("name"));
                t.setTaskType(r.getString("type"));
                if (!isBlank(r.getString("run_time"))) t.setRunTime(LocalTime.parse(r.getString("run_time")));
                if (!isBlank(r.getString("end_time"))) t.setEndTime(LocalTime.parse(r.getString("end_time")));
                t.setIntervalMinutes(r.getInt("interval_minutes") <= 0 ? 5 : r.getInt("interval_minutes"));
                t.setIncludeDeviceCheck(r.getInt("include_device_check") == 1);
                t.setIncludePortCheck(r.getInt("include_port_check") == 1);
                t.setIncludeSnmpOidCheck(r.getInt("include_snmp_oid_check") == 1);
                t.setEnabled(r.getInt("enabled") == 1);
                config.getScheduledTasks().add(t);
            }
        }
    }

    private void replaceTasks(Connection c, AppConfig config) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("delete from scheduled_tasks");
        }
        String sql = "insert into scheduled_tasks(name,type,run_time,end_time,interval_minutes,include_device_check,include_port_check,include_snmp_oid_check,enabled) values(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (ScheduledTask t : config.getScheduledTasks()) {
                p.setString(1, value(t.getName()));
                p.setString(2, value(t.getTaskType()));
                p.setString(3, t.getRunTime() == null ? "" : t.getRunTime().toString());
                p.setString(4, t.getEndTime() == null ? "" : t.getEndTime().toString());
                p.setInt(5, t.getIntervalMinutes());
                p.setInt(6, t.isIncludeDeviceCheck() ? 1 : 0);
                p.setInt(7, t.isIncludePortCheck() ? 1 : 0);
                p.setInt(8, t.isIncludeSnmpOidCheck() ? 1 : 0);
                p.setInt(9, t.isEnabled() ? 1 : 0);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private String kv(Connection c, String key, String fallback) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("select value from app_kv where `key`=?")) {
            p.setString(1, key);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getString(1) : fallback;
            }
        }
    }

    private void put(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("update app_kv set value=? where `key`=?")) {
            p.setString(1, value);
            p.setString(2, key);
            if (p.executeUpdate() > 0) return;
        }
        try (PreparedStatement p = c.prepareStatement("insert into app_kv(`key`,value) values(?,?)")) {
            p.setString(1, key);
            p.setString(2, value);
            p.executeUpdate();
        }
    }

    public void saveHistoryJson(String json) {
        init();
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.executeUpdate("delete from history_json");
            }
            try (PreparedStatement p = c.prepareStatement("insert into history_json(task_name, trigger_type, json) values(?,?,?)")) {
                p.setString(1, extract(json, "\"taskName\": \""));
                p.setString(2, extract(json, "\"triggerType\": \""));
                p.setString(3, json);
                p.executeUpdate();
            }
            c.commit();
        } catch (Exception ignored) {
        }
    }

    public void saveInspectionSnapshot(InspectionSnapshot snapshot, String rawJson) {
        init();
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            updateCurrentSummary(c, snapshot, rawJson);
            updateDeviceCurrentStatus(c, snapshot);
            updatePortCurrentStatus(c, snapshot);
            updateSnmpOidCurrentStatus(c, snapshot);
            replaceCurrentMqttMessages(c, snapshot);
            c.commit();
        } catch (Exception ignored) {
        }
    }

    private long insertRun(Connection c, InspectionSnapshot snapshot, String rawJson) throws SQLException {
        String sql = "insert into inspection_runs(task_name,trigger_type,start_time,end_time,raw_json) values(?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, value(snapshot.getTaskName()));
            p.setString(2, value(snapshot.getTriggerType()));
            p.setString(3, snapshot.getStartTime() == null ? "" : snapshot.getStartTime().toString());
            p.setString(4, snapshot.getEndTime() == null ? "" : snapshot.getEndTime().toString());
            p.setString(5, value(rawJson));
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                return r.next() ? r.getLong(1) : -1;
            }
        }
    }

    private void insertDeviceResults(Connection c, long runId, InspectionSnapshot snapshot) throws SQLException {
        String sql = "insert into device_check_results(run_id,name,type,ip,area,check_method,port,online,response_time_ms,failure_reason,check_time,remark) values(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (CheckResult r : snapshot.getDeviceResults()) {
                Device d = r.getDevice();
                p.setLong(1, runId);
                p.setString(2, value(d.getName()));
                p.setString(3, value(d.getType()));
                p.setString(4, value(d.getIp()));
                p.setString(5, value(d.getArea()));
                p.setString(6, value(d.getCheckMethod()));
                p.setInt(7, d.getPort());
                p.setInt(8, r.isOnline() ? 1 : 0);
                p.setLong(9, r.getResponseTimeMs());
                p.setString(10, value(r.getFailureReason()));
                p.setString(11, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(12, value(d.getRemark()));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void insertPortResults(Connection c, long runId, InspectionSnapshot snapshot) throws SQLException {
        String sql = "insert into port_check_results(run_id,switch_name,switch_ip,area,port_name,port_descr,if_index,speed_mbps,alert_speed_mbps,success,alert,message,check_time) values(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (PortCheckResult r : snapshot.getPortResults()) {
                PortMonitor m = r.getMonitor();
                p.setLong(1, runId);
                p.setString(2, value(m.getSwitchName()));
                p.setString(3, value(m.getSwitchIp()));
                p.setString(4, value(m.getArea()));
                p.setString(5, value(r.getIfName()));
                p.setString(6, value(r.getIfDescr()));
                p.setInt(7, r.getIfIndex());
                p.setLong(8, r.getSpeedMbps());
                p.setInt(9, m.getAlertSpeedMbps());
                p.setInt(10, r.isSuccess() ? 1 : 0);
                p.setInt(11, r.isAlert() ? 1 : 0);
                p.setString(12, value(r.getMessage()));
                p.setString(13, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void insertSnmpOidResults(Connection c, long runId, InspectionSnapshot snapshot) throws SQLException {
        String sql = "insert into snmp_oid_check_results(run_id,name,target_ip,area,oid,value,variable_type,success,message,check_time) values(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (SnmpOidResult r : snapshot.getSnmpOidResults()) {
                SnmpOidMonitor m = r.getMonitor();
                p.setLong(1, runId);
                p.setString(2, value(m.getName()));
                p.setString(3, value(m.getTargetIp()));
                p.setString(4, value(m.getArea()));
                p.setString(5, value(m.getOid()));
                p.setString(6, value(r.getValue()));
                p.setString(7, value(r.getVariableType()));
                p.setInt(8, r.isSuccess() ? 1 : 0);
                p.setString(9, value(r.getMessage()));
                p.setString(10, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void insertMqttMessages(Connection c, long runId, InspectionSnapshot snapshot) throws SQLException {
        String sql = "insert into inspection_mqtt_messages(run_id,message) values(?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (String message : snapshot.getMqttMessages()) {
                p.setLong(1, runId);
                p.setString(2, value(message));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private void updateCurrentSummary(Connection c, InspectionSnapshot snapshot, String rawJson) throws SQLException {
        int online = 0;
        for (CheckResult r : snapshot.getDeviceResults()) if (r.isOnline()) online++;
        int portAlerts = 0;
        int portFailed = 0;
        for (PortCheckResult r : snapshot.getPortResults()) {
            if (r.isAlert()) portAlerts++;
            if (!r.isSuccess()) portFailed++;
        }
        int snmpOidSuccess = 0;
        for (SnmpOidResult r : snapshot.getSnmpOidResults()) if (r.isSuccess()) snmpOidSuccess++;
        String now = java.time.LocalDateTime.now().toString();
        String update = "update inspection_current_summary set task_name=?,trigger_type=?,start_time=?,end_time=?,device_total=?,device_online=?,device_offline=?,port_total=?,port_alerts=?,port_failed=?,snmp_oid_total=?,snmp_oid_success=?,snmp_oid_failed=?,updated_at=?,raw_json=? where id=1";
        try (PreparedStatement p = c.prepareStatement(update)) {
            bindSummary(p, snapshot, rawJson, online, portAlerts, portFailed, snmpOidSuccess, now, false);
            if (p.executeUpdate() > 0) return;
        }
        String insert = "insert into inspection_current_summary(id,task_name,trigger_type,start_time,end_time,device_total,device_online,device_offline,port_total,port_alerts,port_failed,snmp_oid_total,snmp_oid_success,snmp_oid_failed,updated_at,raw_json) values(1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(insert)) {
            bindSummary(p, snapshot, rawJson, online, portAlerts, portFailed, snmpOidSuccess, now, true);
            p.executeUpdate();
        }
    }

    private void bindSummary(PreparedStatement p, InspectionSnapshot snapshot, String rawJson, int online, int portAlerts, int portFailed, int snmpOidSuccess, String now, boolean insert) throws SQLException {
        int i = 1;
        p.setString(i++, value(snapshot.getTaskName()));
        p.setString(i++, value(snapshot.getTriggerType()));
        p.setString(i++, snapshot.getStartTime() == null ? "" : snapshot.getStartTime().toString());
        p.setString(i++, snapshot.getEndTime() == null ? "" : snapshot.getEndTime().toString());
        p.setInt(i++, snapshot.getDeviceResults().size());
        p.setInt(i++, online);
        p.setInt(i++, snapshot.getDeviceResults().size() - online);
        p.setInt(i++, snapshot.getPortResults().size());
        p.setInt(i++, portAlerts);
        p.setInt(i++, portFailed);
        p.setInt(i++, snapshot.getSnmpOidResults().size());
        p.setInt(i++, snmpOidSuccess);
        p.setInt(i++, snapshot.getSnmpOidResults().size() - snmpOidSuccess);
        p.setString(i++, now);
        p.setString(i++, value(rawJson));
    }

    private void updateDeviceCurrentStatus(Connection c, InspectionSnapshot snapshot) throws SQLException {
        if (snapshot.getDeviceResults().isEmpty()) return;
        String now = java.time.LocalDateTime.now().toString();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (CheckResult r : snapshot.getDeviceResults()) {
            Device d = r.getDevice();
            String ip = value(d.getIp());
            if (ip.isEmpty()) continue;
            seen.add(ip);
            String update = "update device_current_status set name=?,type=?,area=?,check_method=?,port=?,online=?,response_time_ms=?,failure_reason=?,check_time=?,remark=?,updated_at=? where ip=?";
            try (PreparedStatement p = c.prepareStatement(update)) {
                p.setString(1, value(d.getName()));
                p.setString(2, value(d.getType()));
                p.setString(3, value(d.getArea()));
                p.setString(4, value(d.getCheckMethod()));
                p.setInt(5, d.getPort());
                p.setInt(6, r.isOnline() ? 1 : 0);
                p.setLong(7, r.getResponseTimeMs());
                p.setString(8, value(r.getFailureReason()));
                p.setString(9, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(10, value(d.getRemark()));
                p.setString(11, now);
                p.setString(12, ip);
                if (p.executeUpdate() > 0) continue;
            }
            String insert = "insert into device_current_status(name,type,ip,area,check_method,port,online,response_time_ms,failure_reason,check_time,remark,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(insert)) {
                p.setString(1, value(d.getName()));
                p.setString(2, value(d.getType()));
                p.setString(3, ip);
                p.setString(4, value(d.getArea()));
                p.setString(5, value(d.getCheckMethod()));
                p.setInt(6, d.getPort());
                p.setInt(7, r.isOnline() ? 1 : 0);
                p.setLong(8, r.getResponseTimeMs());
                p.setString(9, value(r.getFailureReason()));
                p.setString(10, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(11, value(d.getRemark()));
                p.setString(12, now);
                p.executeUpdate();
            }
        }
        deleteStaleDevices(c, seen);
    }

    private void deleteStaleDevices(Connection c, java.util.Set<String> ips) throws SQLException {
        if (ips.isEmpty()) return;
        StringBuilder sql = new StringBuilder("delete from device_current_status where ip not in (");
        for (int i = 0; i < ips.size(); i++) sql.append(i == 0 ? "?" : ",?");
        sql.append(")");
        try (PreparedStatement p = c.prepareStatement(sql.toString())) {
            int i = 1;
            for (String ip : ips) p.setString(i++, ip);
            p.executeUpdate();
        }
    }

    private void updatePortCurrentStatus(Connection c, InspectionSnapshot snapshot) throws SQLException {
        if (snapshot.getPortResults().isEmpty()) return;
        String now = java.time.LocalDateTime.now().toString();
        for (PortCheckResult r : snapshot.getPortResults()) {
            PortMonitor m = r.getMonitor();
            String switchIp = value(m.getSwitchIp());
            String portKey = currentPortKey(m, r);
            String update = "update port_current_status set switch_name=?,area=?,port_name=?,port_descr=?,if_index=?,speed_mbps=?,alert_speed_mbps=?,success=?,alert=?,message=?,check_time=?,updated_at=? where switch_ip=? and port_key=?";
            try (PreparedStatement p = c.prepareStatement(update)) {
                p.setString(1, value(m.getSwitchName()));
                p.setString(2, value(m.getArea()));
                p.setString(3, value(r.getIfName()));
                p.setString(4, value(r.getIfDescr()));
                p.setInt(5, r.getIfIndex());
                p.setLong(6, r.getSpeedMbps());
                p.setInt(7, m.getAlertSpeedMbps());
                p.setInt(8, r.isSuccess() ? 1 : 0);
                p.setInt(9, r.isAlert() ? 1 : 0);
                p.setString(10, value(r.getMessage()));
                p.setString(11, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(12, now);
                p.setString(13, switchIp);
                p.setString(14, portKey);
                if (p.executeUpdate() > 0) continue;
            }
            String insert = "insert into port_current_status(switch_name,switch_ip,area,port_key,port_name,port_descr,if_index,speed_mbps,alert_speed_mbps,success,alert,message,check_time,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(insert)) {
                p.setString(1, value(m.getSwitchName()));
                p.setString(2, switchIp);
                p.setString(3, value(m.getArea()));
                p.setString(4, portKey);
                p.setString(5, value(r.getIfName()));
                p.setString(6, value(r.getIfDescr()));
                p.setInt(7, r.getIfIndex());
                p.setLong(8, r.getSpeedMbps());
                p.setInt(9, m.getAlertSpeedMbps());
                p.setInt(10, r.isSuccess() ? 1 : 0);
                p.setInt(11, r.isAlert() ? 1 : 0);
                p.setString(12, value(r.getMessage()));
                p.setString(13, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(14, now);
                p.executeUpdate();
            }
        }
    }

    private String currentPortKey(PortMonitor m, PortCheckResult r) {
        if (m != null && !isBlank(m.getPortKey())) return m.getPortKey();
        if (r.getIfIndex() > 0) return "ifIndex:" + r.getIfIndex();
        return value(r.getIfName());
    }

    private void updateSnmpOidCurrentStatus(Connection c, InspectionSnapshot snapshot) throws SQLException {
        if (snapshot.getSnmpOidResults().isEmpty()) return;
        String now = java.time.LocalDateTime.now().toString();
        for (SnmpOidResult r : snapshot.getSnmpOidResults()) {
            SnmpOidMonitor m = r.getMonitor();
            String update = "update snmp_oid_current_status set name=?,area=?,value=?,variable_type=?,success=?,message=?,check_time=?,updated_at=? where target_ip=? and oid=?";
            try (PreparedStatement p = c.prepareStatement(update)) {
                p.setString(1, value(m.getName()));
                p.setString(2, value(m.getArea()));
                p.setString(3, value(r.getValue()));
                p.setString(4, value(r.getVariableType()));
                p.setInt(5, r.isSuccess() ? 1 : 0);
                p.setString(6, value(r.getMessage()));
                p.setString(7, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(8, now);
                p.setString(9, value(m.getTargetIp()));
                p.setString(10, value(m.getOid()));
                if (p.executeUpdate() > 0) continue;
            }
            String insert = "insert into snmp_oid_current_status(name,target_ip,area,oid,value,variable_type,success,message,check_time,updated_at) values(?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(insert)) {
                p.setString(1, value(m.getName()));
                p.setString(2, value(m.getTargetIp()));
                p.setString(3, value(m.getArea()));
                p.setString(4, value(m.getOid()));
                p.setString(5, value(r.getValue()));
                p.setString(6, value(r.getVariableType()));
                p.setInt(7, r.isSuccess() ? 1 : 0);
                p.setString(8, value(r.getMessage()));
                p.setString(9, r.getCheckTime() == null ? "" : r.getCheckTime().toString());
                p.setString(10, now);
                p.executeUpdate();
            }
        }
    }

    private void replaceCurrentMqttMessages(Connection c, InspectionSnapshot snapshot) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("delete from inspection_current_mqtt_messages");
        }
        String sql = "insert into inspection_current_mqtt_messages(message,updated_at) values(?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            String now = java.time.LocalDateTime.now().toString();
            for (String message : snapshot.getMqttMessages()) {
                p.setString(1, value(message));
                p.setString(2, now);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private String extract(String json, String token) {
        int start = json.indexOf(token);
        if (start < 0) return "";
        start += token.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "";
    }

    private void migrateOldPropertiesIfNeeded() {
        if (!oldPropertiesFile.exists()) return;
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("select count(*) from app_kv")) {
            if (r.next() && r.getInt(1) > 0) return;
        } catch (Exception e) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(oldPropertiesFile)) {
            props.load(in);
            AppConfig config = loadOldProperties(props);
            save(config);
        } catch (Exception ignored) {
        }
    }

    private AppConfig loadOldProperties(Properties props) {
        AppConfig config = new AppConfig();
        MqttConfig mqtt = config.getMqttConfig();
        mqtt.setHost(props.getProperty("mqtt.host", ""));
        mqtt.setPort(intValue(props.getProperty("mqtt.port"), 1883));
        mqtt.setUsername(props.getProperty("mqtt.username", ""));
        mqtt.setPassword(props.getProperty("mqtt.password", ""));
        mqtt.setTopic(props.getProperty("mqtt.topic", ""));
        int monitorCount = intValue(props.getProperty("monitor.count"), 0);
        for (int i = 0; i < monitorCount; i++) {
            PortMonitor monitor = new PortMonitor();
            monitor.setSwitchName(props.getProperty("monitor." + i + ".switchName", ""));
            monitor.setSwitchIp(props.getProperty("monitor." + i + ".switchIp", ""));
            monitor.setArea(props.getProperty("monitor." + i + ".area", ""));
            monitor.setSnmpVersion(props.getProperty("monitor." + i + ".snmpVersion", "2c"));
            monitor.setSnmpCommunity(props.getProperty("monitor." + i + ".snmpCommunity", "public"));
            monitor.setMatchMode(props.getProperty("monitor." + i + ".matchMode", "ifName"));
            monitor.setPortKey(props.getProperty("monitor." + i + ".portKey", ""));
            monitor.setAlertSpeedMbps(intValue(props.getProperty("monitor." + i + ".alertSpeedMbps"), 100));
            monitor.setRemark(props.getProperty("monitor." + i + ".remark", ""));
            config.getPortMonitors().add(monitor);
        }
        int taskCount = intValue(props.getProperty("task.count"), 0);
        for (int i = 0; i < taskCount; i++) {
            ScheduledTask task = new ScheduledTask();
            task.setName(props.getProperty("task." + i + ".name", "任务" + (i + 1)));
            task.setTaskType(props.getProperty("task." + i + ".type", "DAILY"));
            if (!isBlank(props.getProperty("task." + i + ".time", ""))) task.setRunTime(LocalTime.parse(props.getProperty("task." + i + ".time")));
            if (!isBlank(props.getProperty("task." + i + ".endTime", ""))) task.setEndTime(LocalTime.parse(props.getProperty("task." + i + ".endTime")));
            task.setIntervalMinutes(intValue(props.getProperty("task." + i + ".intervalMinutes"), 5));
            task.setIncludeDeviceCheck(Boolean.parseBoolean(props.getProperty("task." + i + ".includeDeviceCheck", "false")));
            task.setIncludePortCheck(Boolean.parseBoolean(props.getProperty("task." + i + ".includePortCheck", "true")));
            task.setEnabled(Boolean.parseBoolean(props.getProperty("task." + i + ".enabled", "true")));
            config.getScheduledTasks().add(task);
        }
        return config;
    }

    private File detectAppDir() {
        try {
            File code = new File(ConfigStore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File parent = code.isFile() ? code.getParentFile() : code;
            if (parent != null && "app".equalsIgnoreCase(parent.getName()) && parent.getParentFile() != null) {
                return parent.getParentFile();
            }
        } catch (Exception ignored) {
        }
        return new File(System.getProperty("user.dir"));
    }

    private void migrateOldSqliteIfNeeded() {
        if (dbFile.exists() || !oldDbFile.exists()) return;
        try {
            java.nio.file.Files.copy(oldDbFile.toPath(), dbFile.toPath());
        } catch (Exception ignored) {
        }
    }

    private DatabaseConfig loadDatabaseConfig() {
        DatabaseConfig cfg = new DatabaseConfig();
        if (!storagePropertiesFile.exists()) return cfg;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(storagePropertiesFile)) {
            props.load(in);
            cfg.setBackend(props.getProperty("database.backend", "SQLITE"));
            cfg.setHost(props.getProperty("mysql.host", "127.0.0.1"));
            cfg.setPort(intValue(props.getProperty("mysql.port"), 3306));
            cfg.setDatabase(props.getProperty("mysql.database", "netpatrol"));
            cfg.setUsername(props.getProperty("mysql.username", "root"));
            cfg.setPassword(props.getProperty("mysql.password", ""));
        } catch (Exception ignored) {
        }
        return cfg;
    }

    private void saveDatabaseConfig(DatabaseConfig cfg) {
        Properties props = new Properties();
        props.setProperty("database.backend", value(cfg.getBackend()));
        props.setProperty("mysql.host", value(cfg.getHost()));
        props.setProperty("mysql.port", String.valueOf(cfg.getPort()));
        props.setProperty("mysql.database", value(cfg.getDatabase()));
        props.setProperty("mysql.username", value(cfg.getUsername()));
        props.setProperty("mysql.password", value(cfg.getPassword()));
        try (FileOutputStream out = new FileOutputStream(storagePropertiesFile)) {
            props.store(out, "NetPatrol storage settings");
        } catch (Exception ignored) {
        }
    }

    private DatabaseConfig copyDatabaseConfig(DatabaseConfig source) {
        DatabaseConfig cfg = new DatabaseConfig();
        if (source == null) return cfg;
        cfg.setBackend(value(source.getBackend()).isEmpty() ? "SQLITE" : source.getBackend());
        cfg.setHost(value(source.getHost()).isEmpty() ? "127.0.0.1" : source.getHost());
        cfg.setPort(source.getPort() <= 0 ? 3306 : source.getPort());
        cfg.setDatabase(value(source.getDatabase()).isEmpty() ? "netpatrol" : source.getDatabase());
        cfg.setUsername(value(source.getUsername()).isEmpty() ? "root" : source.getUsername());
        cfg.setPassword(value(source.getPassword()));
        return cfg;
    }

    private int intValue(String text, int fallback) {
        try {
            return text == null || text.trim().isEmpty() ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
