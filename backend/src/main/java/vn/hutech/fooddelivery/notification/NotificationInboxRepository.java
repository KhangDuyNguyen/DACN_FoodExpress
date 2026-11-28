package vn.hutech.fooddelivery.notification;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
class NotificationInboxRepository {
    private final NamedParameterJdbcTemplate jdbc;

    NotificationInboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Row> page(long userId, boolean unreadOnly, NotificationCursor cursor, int size) {
        var params = new MapSqlParameterSource("userId", userId).addValue("size", size);
        String sql = """
                SELECT id, type, title, body, data_json, is_read, created_at
                FROM notifications WHERE user_id = :userId
                """;
        if (unreadOnly) {
            sql += " AND is_read = 0";
        }
        if (cursor != null) {
            sql += " AND (created_at < :createdAt OR (created_at = :createdAt AND id < :id))";
            params.addValue("createdAt", cursor.createdAt()).addValue("id", cursor.id());
        }
        sql += " ORDER BY created_at DESC, id DESC LIMIT :size";
        return jdbc.query(sql, params, (rs, index) -> new Row(
                rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getString("body"),
                rs.getString("data_json"), rs.getBoolean("is_read"), rs.getObject("created_at", LocalDateTime.class)));
    }

    long unreadCount(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = 0
                """, Map.of("userId", userId), Long.class));
    }

    boolean markRead(long userId, long id) {
        var params = Map.of("userId", userId, "id", id);
        int updated = jdbc.update("""
                UPDATE notifications SET is_read = 1 WHERE id = :id AND user_id = :userId AND is_read = 0
                """, params);
        return updated == 1 || !jdbc.queryForList("""
                SELECT id FROM notifications WHERE id = :id AND user_id = :userId FOR UPDATE
                """, params, Long.class).isEmpty();
    }

    long insert(long userId, String type, String eventKey, String title, String body,
                String dataJson, LocalDateTime createdAt) {
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO notifications (user_id, type, event_key, title, body, data_json, created_at)
                VALUES (:userId, :type, :eventKey, :title, :body, :data, :createdAt)
                """, new MapSqlParameterSource(Map.of("userId", userId, "type", type, "eventKey", eventKey,
                "title", title, "body", body, "data", dataJson, "createdAt", createdAt)), key, new String[]{"id"});
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    Optional<Long> existingEvent(long userId, String eventKey) {
        // A locking read sees the committed winner even if this transaction already used a snapshot.
        return jdbc.queryForList("""
                SELECT id FROM notifications WHERE user_id = :userId AND event_key = :eventKey FOR SHARE
                """, Map.of("userId", userId, "eventKey", eventKey), Long.class).stream().findFirst();
    }

    record Row(long id, String type, String title, String body, String dataJson, boolean isRead,
               LocalDateTime createdAt) {}
}
