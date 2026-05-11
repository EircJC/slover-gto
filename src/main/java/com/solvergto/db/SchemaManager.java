package com.solvergto.db;

import com.solvergto.config.SolverDatabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SchemaManager {
    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private final DataSource dataSource;
    private final SolverDatabaseProperties properties;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean trainerInitialized = new AtomicBoolean(false);

    public SchemaManager(DataSource dataSource, SolverDatabaseProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    public synchronized void initialize() throws SQLException, IOException {
        if (initialized.get()) {
            return;
        }
        log.info("Initializing full schema for database `{}` at {}:{}",
                properties.getDatabaseName(), properties.getHost(), properties.getPort());
        try (Connection connection = DriverManager.getConnection(properties.serverUrl(), properties.getUser(), properties.getPassword())) {
            execute(connection, "CREATE DATABASE IF NOT EXISTS `" + properties.getDatabaseName() + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        try (Connection connection = dataSource.getConnection()) {
            for (String statement : readStatements("/sql/schema.sql")) {
                execute(connection, statement);
            }
            ensureTrainerCompatibility(connection);
        }
        initialized.set(true);
        trainerInitialized.set(true);
        log.info("Full schema initialization completed for database `{}`", properties.getDatabaseName());
    }

    public synchronized void ensureTrainerSchema() throws SQLException, IOException {
        if (trainerInitialized.get()) {
            return;
        }
        log.info("Ensuring trainer schema compatibility for database `{}`", properties.getDatabaseName());
        try (Connection connection = dataSource.getConnection()) {
            ensureTrainerTables(connection);
            ensureTrainerCompatibility(connection);
        } catch (SQLException | IOException exception) {
            log.error("Trainer schema compatibility check failed for database `{}`: {}",
                    properties.getDatabaseName(), exception.getMessage(), exception);
            throw exception;
        }
        trainerInitialized.set(true);
        log.info("Trainer schema compatibility check completed for database `{}`", properties.getDatabaseName());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        log.debug("Executing SQL: {}", shortenSql(sql));
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

    private void ensureTrainerTables(Connection connection) throws SQLException, IOException {
        for (String statement : readStatements("/sql/schema.sql")) {
            if (!isTrainerCreateStatement(statement)) {
                continue;
            }
            execute(connection, statement);
        }
    }

    private void ensureTrainerCompatibility(Connection connection) throws SQLException {
        ensureTableExists(connection, "player");
        ensureTableExists(connection, "trainer_session");
        ensureTableExists(connection, "trainer_hand_history");
        ensureTableExists(connection, "trainer_operation");
        ensureStatusColumnLength(connection);
        ensureColumnExists(
                connection,
                "trainer_hand_history",
                "opponent_hand",
                "ALTER TABLE trainer_hand_history ADD COLUMN opponent_hand VARCHAR(4) NULL COMMENT 'GTO对手两张手牌，例如KhQh，未摊牌时也会按训练样本保存' AFTER hero_hand"
        );
        ensureColumnExists(
                connection,
                "trainer_hand_history",
                "outcome_label",
                "ALTER TABLE trainer_hand_history ADD COLUMN outcome_label VARCHAR(64) NULL COMMENT '该手牌最终结果说明，例如Hero赢下摊牌、Hero弃牌告负或双方平分底池' AFTER hand_score"
        );
    }

    private void ensureStatusColumnLength(Connection connection) throws SQLException {
        int size = columnSize(connection, "trainer_session", "status");
        if (size >= 16) {
            log.info("Column trainer_session.status already supports length {}", size);
            return;
        }
        log.info("Expanding trainer_session.status column length from {} to 16", size);
        execute(connection, "ALTER TABLE trainer_session MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'session状态，RUNNING、HAND_DONE或COMPLETED'");
    }

    private void ensureColumnExists(Connection connection, String tableName, String columnName, String alterSql) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            log.info("Column {}.{} already exists", tableName, columnName);
            return;
        }
        log.info("Adding missing column {}.{}", tableName, columnName);
        execute(connection, alterSql);
    }

    private void ensureTableExists(Connection connection, String tableName) throws SQLException {
        if (!tableExists(connection, tableName)) {
            throw new SQLException("Required trainer table missing: " + tableName);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(
                connection.getCatalog(),
                null,
                tableName,
                new String[]{"TABLE"}
        )) {
            while (tables.next()) {
                String existing = tables.getString("TABLE_NAME");
                if (existing != null && existing.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(
                connection.getCatalog(),
                null,
                tableName,
                null
        )) {
            while (columns.next()) {
                String existing = columns.getString("COLUMN_NAME");
                if (existing != null && existing.toLowerCase(Locale.ROOT).equals(columnName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    private int columnSize(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(
                connection.getCatalog(),
                null,
                tableName,
                columnName
        )) {
            if (columns.next()) {
                return columns.getInt("COLUMN_SIZE");
            }
            return 0;
        }
    }

    private boolean isTrainerCreateStatement(String statement) {
        String normalized = statement.stripLeading().toUpperCase(Locale.ROOT);
        return normalized.startsWith("CREATE TABLE IF NOT EXISTS PLAYER")
                || normalized.startsWith("CREATE TABLE IF NOT EXISTS TRAINER_SESSION")
                || normalized.startsWith("CREATE TABLE IF NOT EXISTS TRAINER_HAND_HISTORY")
                || normalized.startsWith("CREATE TABLE IF NOT EXISTS TRAINER_OPERATION");
    }

    private String shortenSql(String sql) {
        String compact = sql.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 177) + "..." : compact;
    }
}
