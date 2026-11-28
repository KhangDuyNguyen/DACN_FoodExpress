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
                SELECT o.id, o.code, o.status, o.customer_id, loc.lat, loc.lng, loc.recorded_at,
                       CASE WHEN o.customer_id = :customerId THEN o.delivery_lat END AS delivery_lat,
                       CASE WHEN o.customer_id = :customerId THEN o.delivery_lng END AS delivery_lng
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
            var deliveryLat = rs.getBigDecimal("delivery_lat");
            var deliveryPoint = deliveryLat == null ? null :
                    new OrderTrackingResponse.DeliveryPoint(deliveryLat, rs.getBigDecimal("delivery_lng"));
            return new TrackingOrder(rs.getLong("customer_id"),
                    new OrderTrackingResponse(rs.getLong("id"), rs.getString("code"), rs.getString("status"),
                            location, deliveryPoint));
        }).stream().findFirst();
    }

    record TrackingOrder(long customerId, OrderTrackingResponse response) {
    }

    Optional<RouteSnapshot> findRouteSnapshot(long orderId, long customerId, boolean withLocation) {
        String sql = """
                SELECT o.customer_id, o.status, o.driver_id, d.vehicle_type, o.delivery_lat, o.delivery_lng,
                       loc.id AS location_id, loc.lat, loc.lng, loc.recorded_at, u.id IS NOT NULL AS allowed
                FROM orders o
                LEFT JOIN users u ON u.id = o.customer_id AND u.id = :customerId
                    AND u.role = 'CUSTOMER' AND u.status NOT IN ('LOCKED', 'DELETED')
                LEFT JOIN drivers d ON d.id = o.driver_id AND u.id IS NOT NULL
                LEFT JOIN driver_locations loc ON :withLocation AND loc.id = (
                    SELECT l.id
                    FROM driver_locations l
                    WHERE l.order_id = o.id AND l.driver_id = o.driver_id
                      AND u.id IS NOT NULL
                    ORDER BY l.recorded_at DESC, l.id DESC
                    LIMIT 1
                )
                WHERE o.id = :orderId
                """;
        return jdbc.query(sql, Map.of("orderId", orderId, "customerId", customerId, "withLocation", withLocation),
                (rs, rowNum) -> {
                    long owner = rs.getLong("customer_id");
                    if (!rs.getBoolean("allowed")) {
                        return new RouteSnapshot(owner, false, null, null, null, null, null);
                    }
                    var locationId = rs.getObject("location_id", Long.class);
                    var fix = locationId == null ? null : new RouteFix(locationId,
                            RoutePoint.from(rs.getBigDecimal("lat"), rs.getBigDecimal("lng")),
                            rs.getObject("recorded_at", LocalDateTime.class));
                    return new RouteSnapshot(owner, true, rs.getString("status"), rs.getObject("driver_id", Long.class),
                            rs.getString("vehicle_type"),
                            RoutePoint.from(rs.getBigDecimal("delivery_lat"), rs.getBigDecimal("delivery_lng")), fix);
                }).stream().findFirst();
    }

    record RouteSnapshot(long customerId, boolean allowed, String status, Long driverId, String vehicleType,
                         RoutePoint destination, RouteFix fix) {
    }

    record RouteFix(long id, RoutePoint point, LocalDateTime recordedAt) {
    }
}
