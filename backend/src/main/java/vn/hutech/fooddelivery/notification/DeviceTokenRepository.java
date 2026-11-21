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
                ON DUPLICATE KEY UPDATE user_id = :userId, platform = 'ANDROID', updated_at = CURRENT_TIMESTAMP
                """, Map.of("userId", userId, "token", token));
    }

    void remove(long userId, String token) {
        jdbc.update("DELETE FROM device_tokens WHERE user_id = :userId AND fcm_token = :token",
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
}
