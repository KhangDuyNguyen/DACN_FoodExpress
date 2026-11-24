package vn.hutech.fooddelivery.driver;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class DriverLocationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DriverLocationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Long> lockActiveOrderIds(long userId) {
        // The outer locking read locks orders only. The driver subquery is a consistent read.
        return jdbc.queryForList("""
                SELECT id FROM orders
                WHERE driver_id = (SELECT id FROM drivers WHERE user_id = :userId)
                  AND status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
                ORDER BY id
                FOR UPDATE
                """, Map.of("userId", userId), Long.class);
    }

    List<ActiveOrder> findActiveOrders(long driverId) {
        return jdbc.query("""
                SELECT id, status FROM orders
                WHERE driver_id = :driverId
                  AND status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
                """, Map.of("driverId", driverId),
                (rs, i) -> new ActiveOrder(rs.getLong("id"), rs.getString("status")));
    }

    public void insert(long driverId, Long orderId, BigDecimal lat, BigDecimal lng, LocalDateTime recordedAt) {
        String sql = """
                INSERT INTO driver_locations (driver_id, order_id, lat, lng, recorded_at)
                VALUES (:driverId, :orderId, :lat, :lng, :recordedAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("driverId", driverId)
                .addValue("orderId", orderId)
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("recordedAt", Timestamp.valueOf(recordedAt));
        jdbc.update(sql, params);
    }

    record ActiveOrder(long id, String status) {
    }
}
