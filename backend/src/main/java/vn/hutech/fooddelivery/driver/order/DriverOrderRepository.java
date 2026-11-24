package vn.hutech.fooddelivery.driver.order;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.driver.DriverReadTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Chi SELECT; khong khoa ban ghi va khong can thay doi cac repository Driver/Assignment hien co. */
@Repository
public class DriverOrderRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DriverOrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // An - Tuan 5C [MODIFY]: Dung chung anh xa danh tinh cho hai API doc, giu nguyen SQL cua GET current.
    public Optional<Long> findDriverId(long userId) {
        return jdbc.query("SELECT id FROM drivers WHERE user_id = :userId", Map.of("userId", userId),
                (rs, index) -> rs.getLong("id")).stream().findFirst();
    }

    public long countDeliveredHistory(long driverId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders WHERE driver_id = :driverId AND status = 'DELIVERED'
                """, Map.of("driverId", driverId), Long.class);
        return count == null ? 0 : count;
    }

    public List<DriverOrderHistoryItem> findDeliveredHistory(long driverId, int limit, long offset) {
        return jdbc.query("""
                SELECT o.id, o.code, r.id AS restaurant_id, r.name AS restaurant_name, o.status,
                       UNIX_TIMESTAMP(o.delivered_at) AS delivered_at_epoch
                FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
                WHERE o.driver_id = :driverId AND o.status = 'DELIVERED'
                ORDER BY o.delivered_at DESC, o.id DESC
                LIMIT :limit OFFSET :offset
                """, Map.of("driverId", driverId, "limit", limit, "offset", offset), (rs, i) ->
                new DriverOrderHistoryItem(rs.getLong("id"), rs.getString("code"), rs.getLong("restaurant_id"),
                        rs.getString("restaurant_name"), rs.getString("status"),
                        DriverReadTime.vietnamTime(rs, "delivered_at_epoch")));
    }

    List<DriverCurrentOrderResponse.CurrentOrder> findCurrentOrders(long driverId) {
        // Cung quy tac voi AssignmentOfferRepository.hasActiveOrder, ke ca PENDING_PAYMENT da gan driver.
        // LIMIT 2 de Service phat hien nhieu don, khong am tham chon don dau tien.
        String sql = """
                SELECT o.id AS order_id, o.code AS order_code, o.status, o.created_at, o.updated_at,
                       r.id AS restaurant_id, r.name AS restaurant_name, r.address AS restaurant_address,
                       r.phone AS restaurant_phone, r.lat AS pickup_lat, r.lng AS pickup_lng,
                       o.receiver_name, o.receiver_phone, o.delivery_address, o.delivery_lat, o.delivery_lng, o.note
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                WHERE o.driver_id = :driverId
                  AND o.status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
                ORDER BY o.id
                LIMIT 2
                """;
        return jdbc.query(sql, Map.of("driverId", driverId), (rs, index) -> new DriverCurrentOrderResponse.CurrentOrder(
                rs.getLong("order_id"), rs.getString("order_code"), rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getLong("restaurant_id"), rs.getString("restaurant_name"), rs.getString("restaurant_address"),
                rs.getString("restaurant_phone"), rs.getBigDecimal("pickup_lat"), rs.getBigDecimal("pickup_lng"),
                rs.getString("receiver_name"), rs.getString("receiver_phone"), rs.getString("delivery_address"),
                rs.getBigDecimal("delivery_lat"), rs.getBigDecimal("delivery_lng"), rs.getString("note")));
    }
}
