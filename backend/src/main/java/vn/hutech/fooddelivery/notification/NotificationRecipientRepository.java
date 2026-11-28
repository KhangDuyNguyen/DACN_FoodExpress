package vn.hutech.fooddelivery.notification;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class NotificationRecipientRepository {
    private final NamedParameterJdbcTemplate jdbc;

    NotificationRecipientRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<OrderRecipients> findOrder(long orderId, long restaurantId) {
        return jdbc.query("""
                SELECT o.code, o.status, o.customer_id, o.driver_id,
                       customer.id AS customer_user_id, owner.id AS merchant_user_id, driver.id AS driver_user_id
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                JOIN merchants m ON m.id = r.merchant_id
                LEFT JOIN users customer ON customer.id = o.customer_id AND customer.role = 'CUSTOMER'
                LEFT JOIN users owner ON owner.id = m.owner_user_id AND owner.role = 'MERCHANT'
                LEFT JOIN drivers d ON d.id = o.driver_id
                LEFT JOIN users driver ON driver.id = d.user_id AND driver.role = 'DRIVER'
                WHERE o.id = :orderId AND o.restaurant_id = :restaurantId
                """, Map.of("orderId", orderId, "restaurantId", restaurantId), (rs, index) -> new OrderRecipients(
                rs.getString("code"), rs.getString("status"), rs.getLong("customer_id"),
                rs.getObject("driver_id", Long.class), rs.getObject("customer_user_id", Long.class),
                rs.getObject("merchant_user_id", Long.class), rs.getObject("driver_user_id", Long.class)))
                .stream().findFirst();
    }

    Long driverUserId(long driverId) {
        return jdbc.queryForList("""
                SELECT u.id FROM drivers d JOIN users u ON u.id = d.user_id AND u.role = 'DRIVER'
                WHERE d.id = :driverId
                """, Map.of("driverId", driverId), Long.class).stream().findFirst().orElse(null);
    }

    List<Long> effectiveOfferDrivers(long orderId, LocalDateTime now) {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id
                FROM order_assignments a
                JOIN drivers d ON d.id = a.driver_id
                JOIN users u ON u.id = d.user_id AND u.role = 'DRIVER'
                WHERE a.order_id = :orderId AND a.status = 'OFFERED' AND a.expires_at > :now
                """, Map.of("orderId", orderId, "now", now), Long.class);
    }

    record OrderRecipients(String code, String status, long orderCustomerId, Long driverId,
                           Long customerUserId, Long merchantUserId, Long driverUserId) {}
}
