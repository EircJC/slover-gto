package com.solvergto.db;

import com.solvergto.config.SolverDatabaseProperties;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Component
public class SchemaManager {
    private final DataSource dataSource;
    private final SolverDatabaseProperties properties;

    public SchemaManager(DataSource dataSource, SolverDatabaseProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    public void initialize() throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection(properties.serverUrl(), properties.getUser(), properties.getPassword())) {
            execute(connection, "CREATE DATABASE IF NOT EXISTS `" + properties.getDatabaseName() + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        try (Connection connection = dataSource.getConnection()) {
            for (String statement : readStatements("/sql/schema.sql")) {
                execute(connection, statement);
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<String> readStatements(String resourcePath) throws IOException {
        try (InputStream in = SchemaManager.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return splitSql(text);
            }
        }
        Path filePath = Path.of(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        if (Files.exists(filePath)) {
            return splitSql(Files.readString(filePath, StandardCharsets.UTF_8));
        }
        throw new IOException("Missing resource: " + resourcePath);
    }

    private List<String> splitSql(String text) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            }
            if (c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                String statement = current.toString().trim();
                if (!statement.isBlank() && !statement.startsWith("--")) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String trailing = current.toString().trim();
        if (!trailing.isBlank() && !trailing.startsWith("--")) {
            statements.add(trailing);
        }
        return statements;
    }
}
