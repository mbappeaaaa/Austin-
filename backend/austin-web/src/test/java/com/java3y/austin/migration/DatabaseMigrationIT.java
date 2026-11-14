package com.java3y.austin.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DatabaseMigrationIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7.44")
            .withDatabaseName("austin_migration_test");

    @Test
    void shouldBaselineLegacySchemaAndInstallFreshSchemaRepeatably() throws SQLException {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_marker (id BIGINT NOT NULL PRIMARY KEY)");
            statement.execute("CREATE TABLE sys_user (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "username VARCHAR(64) NOT NULL UNIQUE)");
            statement.execute("INSERT INTO sys_user (username) VALUES ('legacy-user')");
            statement.execute("CREATE TABLE message_template (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "is_deleted TINYINT NOT NULL DEFAULT 0, updated INT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE channel_account (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "is_deleted TINYINT NOT NULL DEFAULT 0, send_channel INT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE sms_record (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "send_date INT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE in_app_message (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "message_id VARCHAR(64) NOT NULL, receiver VARCHAR(128) NOT NULL, "
                    + "is_deleted TINYINT NOT NULL DEFAULT 0, created INT NOT NULL DEFAULT 0, "
                    + "UNIQUE KEY uk_in_app_message_receiver (message_id, receiver))");
        }

        Flyway legacyFlyway = flyway(true, false);
        assertEquals(9, legacyFlyway.migrate().migrationsExecuted);
        assertTrue(tableExists("legacy_marker"));
        assertTrue(tableExists("tenant"));
        assertEquals(1L, queryLong("SELECT tenant_id FROM sys_user WHERE username = 'legacy-user'"));
        assertTrue(columnExists("message_template", "tenant_id"));
        assertTrue(columnExists("channel_account", "tenant_id"));
        assertTrue(columnExists("sms_record", "tenant_id"));
        assertTrue(columnExists("in_app_message", "tenant_id"));
        assertTrue(tableExists("billing_quota_account"));
        assertTrue(columnExists("billing_quota_account", "alert_cycle"));
        assertTrue(columnExists("billing_quota_account", "threshold_alert_active"));
        assertTrue(columnExists("billing_quota_account", "insufficient_alert_active"));
        assertTrue(tableExists("billing_quota_reservation"));
        assertTrue(columnExists("billing_quota_reservation", "settled_amount"));
        assertTrue(columnExists("billing_quota_reservation", "released_amount"));
        assertTrue(tableExists("billing_quota_ledger"));
        assertTrue(tableExists("mq_consume_inbox"));
        assertTrue(tableExists("billing_payment_order"));
        assertTrue(tableExists("billing_payment_event"));
        assertTrue(tableExists("billing_outbox_event"));
        assertTrue(columnExists("billing_outbox_event", "locked_until"));
        assertTrue(columnExists("billing_outbox_event", "max_attempts"));
        assertTrue(tableExists("report_generation_job"));
        assertTrue(indexExists("billing_payment_event", "uk_payment_event_provider_id", true));
        assertTrue(indexExists("mq_consume_inbox", "uk_mq_inbox_tenant_group_key", true));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM billing_quota_account WHERE tenant_id = 1"));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'BASELINE'"));

        flyway(false, true).clean();

        Flyway freshFlyway = flyway(false, false);
        assertEquals(10, freshFlyway.migrate().migrationsExecuted);
        assertTrue(tableExists("message_template"));
        assertTrue(tableExists("sms_record"));
        assertTrue(tableExists("channel_account"));
        assertTrue(tableExists("in_app_message"));
        assertTrue(tableExists("sys_user"));
        assertTrue(tableExists("tenant"));
        assertTrue(tableExists("billing_quota_account"));
        assertTrue(columnExists("billing_quota_account", "alert_cycle"));
        assertTrue(columnExists("billing_quota_account", "threshold_alert_active"));
        assertTrue(columnExists("billing_quota_account", "insufficient_alert_active"));
        assertTrue(tableExists("billing_quota_reservation"));
        assertTrue(columnExists("billing_quota_reservation", "settled_amount"));
        assertTrue(columnExists("billing_quota_reservation", "released_amount"));
        assertTrue(tableExists("billing_quota_ledger"));
        assertTrue(tableExists("mq_consume_inbox"));
        assertTrue(tableExists("billing_quota_package"));
        assertTrue(tableExists("billing_payment_order"));
        assertTrue(tableExists("billing_payment_event"));
        assertTrue(tableExists("billing_outbox_event"));
        assertTrue(columnExists("billing_outbox_event", "locked_until"));
        assertTrue(columnExists("billing_outbox_event", "replay_count"));
        assertTrue(tableExists("report_generation_job"));
        assertTrue(columnExists("report_generation_job", "storage_key"));
        assertTrue(indexExists("mq_consume_inbox", "uk_mq_inbox_tenant_group_key", true));
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM tenant WHERE tenant_code = 'legacy'"));
        assertEquals(0, freshFlyway.migrate().migrationsExecuted);
    }

    private Flyway flyway(boolean baselineOnMigrate, boolean cleanEnabled) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .cleanDisabled(!cleanEnabled)
                .load();
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'";
        return queryLong(sql) == 1L;
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = '" + tableName + "' AND column_name = '" + columnName + "'";
        return queryLong(sql) == 1L;
    }

    private boolean indexExists(String tableName, String indexName, boolean unique) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name = '" + tableName + "' AND index_name = '" + indexName + "' "
                + "AND non_unique = " + (unique ? 0 : 1);
        return queryLong(sql) > 0L;
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
