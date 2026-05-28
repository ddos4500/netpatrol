package com.netpatrol.model;

public class DatabaseConfig {
    private String backend = "SQLITE";
    private String host = "127.0.0.1";
    private int port = 3306;
    private String database = "netpatrol";
    private String username = "root";
    private String password = "";

    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isMysql() {
        return "MYSQL".equalsIgnoreCase(backend);
    }
}
