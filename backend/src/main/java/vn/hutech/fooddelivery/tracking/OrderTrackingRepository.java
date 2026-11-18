package vn.hutech.fooddelivery.tracking;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Repository
public class OrderTrackingRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OrderTrackingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean isOwnedBy(long orderId, long customerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM orders WHERE id = :orderId AND customer_id = :customerId
                )
                """, Map.of("orderId", orderId, "customerId", customerId), Boolean.class));
    }

    Optional<TrackingOrder> findTracking(long orderId, long customerId) {
        String sql = """
                SELECT o.id, o.code, o.status, o.customer_id, loc.lat, loc.lng, loc.recorded_at
                FROM orders o
                LEFT JOIN driver_locations loc ON loc.id = (
                    SELECT l.id
                    FROM driver_locations l
                    WHERE l.order_id = o.id AND l.driver_id = o.driver_id
                      AND o.customer_id = :customerId
                    ORDER BY l.recorded_at DESC, l.id DESC
                    LIMIT 1
                )
                WHERE o.id = :orderId
                """;
        return jdbc.query(sql, Map.of("orderId", orderId, "customerId", customerId), (rs, rowNum) -> {
            LocalDateTime recordedAt = rs.getObject("recorded_at", LocalDateTime.class);
            OrderTrackingResponse.Location location = recordedAt == null ? null :
                    new OrderTrackingResponse.Location(rs.getBigDecimal("lat"), rs.getBigDecimal("lng"), recordedAt);
            return new TrackingOrder(rs.getLong("customer_id"),
                    new OrderTrackingResponse(rs.getLong("id"), rs.getString("code"), rs.getString("status"), location));
        }).stream().findFirst();
    }

    record TrackingOrder(long customerId, OrderTrackingResponse response) {
    }
}
