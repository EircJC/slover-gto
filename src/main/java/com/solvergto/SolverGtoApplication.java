package com.solvergto;

import com.solvergto.config.SolverDatabaseProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SolverDatabaseProperties.class)
@MapperScan("com.solvergto.db.mapper")
public class SolverGtoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SolverGtoApplication.class, args);
    }
}
