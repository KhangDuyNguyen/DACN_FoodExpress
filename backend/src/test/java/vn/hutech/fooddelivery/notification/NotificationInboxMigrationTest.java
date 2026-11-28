package vn.hutech.fooddelivery.notification;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class NotificationInboxMigrationTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void upgradesExistingSchemaPreservingLegacyRowsAndEnforcingOnlyRecipientEventUniqueness() {
        var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("20261108.01"))
                .load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'event_key'
                """, Integer.class));
        for (long id : List.of(754801L, 754802L)) {
            jdbc.update("""
                    INSERT INTO users (id, full_name, phone, password_hash, role)
                    VALUES (?, 'Migration fixture', ?, 'fixture-only', 'CUSTOMER')
                    """, id, "0978" + id);
        }
        jdbc.update("""
                INSERT INTO notifications (id, user_id, type, title, body, data_json, is_read, created_at)
                VALUES (754810, 754801, 'ORDER', 'Legacy order', 'Legacy body', '{"orderId":11}', 1, '2026-11-26 15:00:00'),
                       (754811, 754801, 'SYSTEM', 'Legacy system', 'Legacy body', NULL, 0, '2026-11-26 15:00:00')
                """);
        var before = legacyRows(jdbc);

        Flyway current = Flyway.configure().dataSource(dataSource).load();
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();
        assertEquals(0, current.migrate().migrationsExecuted);
        assertEquals(before, legacyRows(jdbc));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE user_id = 754801 AND event_key IS NULL
                """, Integer.class));

        insert(jdbc, 754801L, null);
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE user_id = 754801 AND event_key IS NULL
                """, Integer.class));
        insert(jdbc, 754801L, "order:11");
        assertThrows(DuplicateKeyException.class, () -> insert(jdbc, 754801L, "order:11"));
        insert(jdbc, 754802L, "order:11");
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE event_key = 'order:11'
                """, Integer.class));
    }

    private List<?> legacyRows(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT id, user_id, type, title, body, data_json, is_read, created_at
                FROM notifications WHERE id IN (754810, 754811) ORDER BY id
                """);
    }

    private void insert(JdbcTemplate jdbc, long userId, String eventKey) {
        jdbc.update("INSERT INTO notifications (user_id, title, body, event_key) VALUES (?, 'test', 'test', ?)",
                userId, eventKey);
    }
}
