package com.solvergto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "solvergto.database")
public class SolverDatabaseProperties {
    private String host = "127.0.0.1";
    private int port = 3306;
    private String databaseName = "solver-gto";
    private String user = "root";
    private String password = "jiang5368166";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String databaseUrl() {
        return baseUrl() + "/" + databaseName + jdbcSuffix();
    }

    public String serverUrl() {
        return baseUrl() + "/" + jdbcSuffix();
    }

    private String baseUrl() {
        return "jdbc:mysql://" + host + ":" + port;
    }

    private String jdbcSuffix() {
        return "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }
}
