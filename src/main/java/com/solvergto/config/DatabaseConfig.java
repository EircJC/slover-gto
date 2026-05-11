package com.solvergto.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    @Bean
    public DataSource dataSource(SolverDatabaseProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(properties.databaseUrl());
        dataSource.setUsername(properties.getUser());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }
}
