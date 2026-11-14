package vn.hutech.fooddelivery.driver.order;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Chi goi trong transaction ghi: Order -> Driver -> Assignment -> Earnings. */
@Repository
public class DriverDeliveryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DriverDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Long> findDriverId(long userId) {
        return jdbc.query("SELECT id FROM drivers WHERE user_id = :userId", Map.of("userId", userId),
                (rs, i) -> rs.getLong("id")).stream().findFirst();
    }

    Optional<DeliveryOrder> lockOrder(long orderId) {
        return jdbc.query("""
                SELECT id, driver_id, status, delivery_fee, updated_at, delivered_at
                FROM orders WHERE id = :orderId FOR UPDATE
                """, Map.of("orderId", orderId), (rs, i) -> new DeliveryOrder(rs.getLong("id"),
                rs.getObject("driver_id", Long.class), rs.getString("status"), rs.getBigDecimal("delivery_fee"),
                time(rs.getTimestamp("updated_at")), time(rs.getTimestamp("delivered_at"))))
                .stream().findFirst();
    }

    boolean lockDriver(long driverId, long userId) {
        return !jdbc.query("SELECT id FROM drivers WHERE id = :id AND user_id = :userId FOR UPDATE",
                Map.of("id", driverId, "userId", userId), (rs, i) -> rs.getLong("id")).isEmpty();
    }

    List<Long> lockAcceptedDriverIds(long orderId) {
        // Khong loc theo driver de phat hien ca du lieu nhieu ACCEPTED/ACCEPTED sai driver.
        return jdbc.query("""
                SELECT driver_id FROM order_assignments
                WHERE order_id = :orderId AND status = 'ACCEPTED' ORDER BY id FOR UPDATE
                """, Map.of("orderId", orderId), (rs, i) -> rs.getLong("driver_id"));
    }

    boolean hasDriverHistory(long orderId, String status, long userId) {
        // Locking read tranh snapshot cu neu ben goi da mo transaction truoc do.
        return !jdbc.query("""
                SELECT id FROM order_status_history
                WHERE order_id = :orderId AND status = :status AND changed_by = :userId
                LIMIT 1 FOR UPDATE
                """, Map.of("orderId", orderId, "status", status, "userId", userId),
                (rs, i) -> rs.getLong("id")).isEmpty();
    }

    private static LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    record DeliveryOrder(long id, Long driverId, String status, BigDecimal deliveryFee,
                         LocalDateTime updatedAt, LocalDateTime deliveredAt) {
        DriverOrderStatusResponse response() {
            return new DriverOrderStatusResponse(id, status, updatedAt, deliveredAt);
        }
    }
}
