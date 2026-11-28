package vn.hutech.fooddelivery.notification;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class DeviceTokenRepository {
    private final NamedParameterJdbcTemplate jdbc;

    DeviceTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void registerAndroid(long userId, String token) {
        jdbc.update("""
                INSERT INTO device_tokens (user_id, fcm_token, platform)
                VALUES (:userId, :token, 'ANDROID')
                ON DUPLICATE KEY UPDATE
                  user_id = IF(CAST(fcm_token AS BINARY) = CAST(:token AS BINARY), :userId, user_id),
                  platform = IF(CAST(fcm_token AS BINARY) = CAST(:token AS BINARY), 'ANDROID', platform),
                  updated_at = IF(CAST(fcm_token AS BINARY) = CAST(:token AS BINARY), CURRENT_TIMESTAMP, updated_at)
                """, Map.of("userId", userId, "token", token));
    }

    void remove(long userId, String token) {
        jdbc.update("""
                DELETE FROM device_tokens WHERE user_id = :userId AND fcm_token = :token
                  AND CAST(fcm_token AS BINARY) = CAST(:token AS BINARY)
                """,
                Map.of("userId", userId, "token", token));
    }

    List<String> findActiveCustomerTokens(long userId) {
        return jdbc.queryForList("""
                SELECT device.fcm_token FROM device_tokens device
                JOIN users owner ON owner.id = device.user_id
                WHERE device.user_id = :userId AND device.platform = 'ANDROID'
                  AND owner.role = 'CUSTOMER' AND owner.status = 'ACTIVE'
                """, Map.of("userId", userId), String.class);
    }

    List<String> findActiveAndroidTokens(long userId) {
        return jdbc.queryForList("""
                SELECT device.fcm_token FROM device_tokens device
                JOIN users owner ON owner.id = device.user_id
                WHERE device.user_id = :userId AND device.platform = 'ANDROID'
                  AND owner.role IN ('CUSTOMER', 'DRIVER') AND owner.status = 'ACTIVE'
                ORDER BY device.id
                """, Map.of("userId", userId), String.class);
    }

    boolean isActiveCustomerBinding(long userId, String token) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM device_tokens device JOIN users owner ON owner.id = device.user_id
                    WHERE device.user_id = :userId AND device.fcm_token = :token
                      AND CAST(device.fcm_token AS BINARY) = CAST(:token AS BINARY)
                      AND device.platform = 'ANDROID' AND owner.role = 'CUSTOMER' AND owner.status = 'ACTIVE'
                )
                """, Map.of("userId", userId, "token", token), Boolean.class));
    }
}
