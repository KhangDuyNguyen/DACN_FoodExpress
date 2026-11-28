package vn.hutech.fooddelivery.notification;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Repository
class NotificationFcmDeliveryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    NotificationFcmDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Delivery> find(CommittedNotificationWriter.SavedNotification saved, String token, LocalDateTime now) {
        return jdbc.query("""
                SELECT u.role, a.expires_at AS offer_expiry
                FROM notifications n
                JOIN users u ON u.id = n.user_id AND u.status = 'ACTIVE' AND u.role IN ('CUSTOMER', 'DRIVER')
                JOIN device_tokens t ON t.user_id = u.id AND t.platform = 'ANDROID' AND t.fcm_token = :token
                    AND CAST(t.fcm_token AS BINARY) = CAST(:token AS BINARY)
                LEFT JOIN order_assignments a ON :eventType = 'OFFER_CREATED'
                    AND a.id = JSON_VALUE(n.data_json, '$.offerId' RETURNING UNSIGNED)
                    AND a.order_id = JSON_VALUE(n.data_json, '$.orderId' RETURNING UNSIGNED)
                    AND a.status = 'OFFERED' AND a.offered_at <= :now AND a.expires_at > :now
                LEFT JOIN drivers d ON d.id = a.driver_id AND d.user_id = u.id AND d.status = 'APPROVED'
                LEFT JOIN orders o ON o.id = a.order_id AND o.status = 'PLACED' AND o.driver_id IS NULL
                WHERE n.id = :notificationId AND n.user_id = :userId AND n.type = 'ORDER'
                    AND JSON_VALUE(n.data_json, '$.eventType') = :eventType
                    AND (:eventType <> 'OFFER_CREATED' OR (d.id IS NOT NULL AND o.id IS NOT NULL))
                """, Map.of("token", token, "eventType", saved.eventType(),
                "notificationId", saved.notificationId(), "userId", saved.userId(),
                "now", now.truncatedTo(ChronoUnit.SECONDS)), (rs, index) ->
                new Delivery(rs.getString("role"), rs.getObject("offer_expiry", LocalDateTime.class)))
                .stream().findFirst();
    }

    record Delivery(String role, LocalDateTime offerExpiry) {}
}
