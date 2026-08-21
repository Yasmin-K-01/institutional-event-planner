package com.example.taskmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaTextColumnUpdater implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchemaTextColumnUpdater.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public SchemaTextColumnUpdater(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String databaseProductName;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            databaseProductName = metadata.getDatabaseProductName().toLowerCase(Locale.ENGLISH);
        }

        List<ColumnUpdate> updates = List.of(
                new ColumnUpdate("calendar_events", "title"),
                new ColumnUpdate("calendar_events", "department"),
                new ColumnUpdate("calendar_events", "category"),
                new ColumnUpdate("calendar_events", "faculty_coordinator"),
                new ColumnUpdate("tasks", "title"),
                new ColumnUpdate("tasks", "description"),
                new ColumnUpdate("tasks", "faculty_coordinator"),
                new ColumnUpdate("calendar_excel_events", "title_of_event"),
                new ColumnUpdate("calendar_excel_events", "faculty_coordinator")
        );

        for (ColumnUpdate update : updates) {
            applyTextColumnUpdate(databaseProductName, update);
        }
    }

    private void applyTextColumnUpdate(String databaseProductName, ColumnUpdate update) {
        String sql = textColumnSql(databaseProductName, update);
        if (sql == null) {
            logger.info("Skipping text column update for unsupported database: {}", databaseProductName);
            return;
        }

        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException exception) {
            logger.warn("Could not widen {}.{} to TEXT: {}", update.table(), update.column(), exception.getMessage());
        }
    }

    private String textColumnSql(String databaseProductName, ColumnUpdate update) {
        if (databaseProductName.contains("postgresql")) {
            return "ALTER TABLE " + update.table() + " ALTER COLUMN " + update.column() + " TYPE TEXT";
        }
        if (databaseProductName.contains("mysql") || databaseProductName.contains("mariadb")) {
            return "ALTER TABLE " + update.table() + " MODIFY COLUMN " + update.column() + " TEXT";
        }
        if (databaseProductName.contains("h2")) {
            return "ALTER TABLE " + update.table() + " ALTER COLUMN " + update.column() + " TEXT";
        }
        return null;
    }

    private record ColumnUpdate(String table, String column) {
    }
}
