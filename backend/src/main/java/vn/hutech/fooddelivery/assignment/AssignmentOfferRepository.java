package vn.hutech.fooddelivery.assignment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Cac thao tac ghi chi duoc goi qua OfferCreationService de giu giao thuc khoa Order -> Driver. */
@Repository
public class AssignmentOfferRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AssignmentOfferRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<OrderSnapshot> findOrder(long orderId) {
        return readOrder(orderId, false);
    }

    Optional<OrderSnapshot> lockOrder(long orderId) {
        return readOrder(orderId, true);
    }

    private Optional<OrderSnapshot> readOrder(long orderId, boolean lock) {
        String sql = "SELECT id, restaurant_id, driver_id, status FROM orders WHERE id = :orderId"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, Map.of("orderId", orderId), (rs, i) -> new OrderSnapshot(
                rs.getLong("id"), rs.getLong("restaurant_id"),
                rs.getObject("driver_id", Long.class), rs.getString("status"))).stream().findFirst();
    }

    Optional<RestaurantLocation> findRestaurant(long restaurantId) {
        return readRestaurant(restaurantId, false);
    }

    Optional<RestaurantLocation> lockRestaurantLocation(long restaurantId) {
        return readRestaurant(restaurantId, true);
    }

    private Optional<RestaurantLocation> readRestaurant(long restaurantId, boolean lock) {
        String sql = "SELECT id, lat, lng FROM restaurants WHERE id = :id" + (lock ? " FOR SHARE" : "");
        return jdbc.query(sql, Map.of("id", restaurantId),
                (rs, i) -> new RestaurantLocation(rs.getLong("id"), rs.getBigDecimal("lat"),
                        rs.getBigDecimal("lng"))).stream().findFirst();
    }

    Optional<DriverCandidate> lockDriver(long driverId) {
        String sql = """
                SELECT id, status, is_online, current_lat, current_lng, last_location_at
                FROM drivers WHERE id = :id FOR UPDATE
                """;
        return jdbc.query(sql, Map.of("id", driverId), (rs, i) -> {
            BigDecimal lat = rs.getBigDecimal("current_lat");
            BigDecimal lng = rs.getBigDecimal("current_lng");
            Timestamp at = rs.getTimestamp("last_location_at");
            // Co busy/offer duoc kiem tra rieng sau khi co khoa Driver.
            return new DriverCandidate(rs.getLong("id"), rs.getString("status"), rs.getBoolean("is_online"),
                    lat == null ? null : lat.doubleValue(), lng == null ? null : lng.doubleValue(),
                    at == null ? null : at.toLocalDateTime(), false, false);
        }).stream().findFirst();
    }

    boolean hasActiveOrder(long driverId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM orders WHERE driver_id = :id
                               AND status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED'))
                """, Map.of("id", driverId), Boolean.class));
    }

    boolean hasOfferedForOrder(long orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM order_assignments WHERE order_id = :id AND status = 'OFFERED')
                """, Map.of("id", orderId), Boolean.class));
    }

    boolean hasOfferedForDriver(long driverId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM order_assignments WHERE driver_id = :id AND status = 'OFFERED')
                """, Map.of("id", driverId), Boolean.class));
    }

    Map<Long, InvitationHistory> findInvitationHistory(long orderId) {
        String sql = """
                SELECT driver_id,
                       MAX(status = 'REJECTED') AS rejected,
                       MAX(status = 'EXPIRED' AND expires_at IS NULL) AS missing_expiry,
                       MAX(CASE WHEN status = 'EXPIRED' THEN expires_at END) AS latest_expired_at
                FROM order_assignments WHERE order_id = :orderId GROUP BY driver_id
                """;
        Map<Long, InvitationHistory> history = new HashMap<>();
        jdbc.query(sql, Map.of("orderId", orderId), (rs, i) -> {
            Timestamp expiredAt = rs.getTimestamp("latest_expired_at");
            history.put(rs.getLong("driver_id"), new InvitationHistory(true, rs.getBoolean("rejected"),
                    rs.getBoolean("missing_expiry"), expiredAt == null ? null : expiredAt.toLocalDateTime()));
            return rs.getLong("driver_id");
        });
        return history;
    }

    AssignmentOffer insertOffer(long orderId, long driverId, double distanceKm, LocalDateTime offeredAt) {
        // Chi lam tron khi ghi cot DECIMAL; khong lam tron khi chon/xep hang.
        BigDecimal storedDistance = BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
        if (storedDistance.signum() < 0 || storedDistance.precision() > 6) {
            throw new IllegalArgumentException("Khoang cach vuot kha nang luu cua distance_km DECIMAL(6,2)");
        }
        LocalDateTime expiresAt = offeredAt.plusSeconds(30);
        var params = new MapSqlParameterSource().addValue("orderId", orderId).addValue("driverId", driverId)
                .addValue("distanceKm", storedDistance).addValue("offeredAt", Timestamp.valueOf(offeredAt))
                .addValue("expiresAt", Timestamp.valueOf(expiresAt));
        var key = new GeneratedKeyHolder();
        int inserted = jdbc.update("""
                INSERT INTO order_assignments (order_id, driver_id, status, distance_km, offered_at, expires_at)
                VALUES (:orderId, :driverId, 'OFFERED', :distanceKm, :offeredAt, :expiresAt)
                """, params, key, new String[]{"id"});
        if (inserted != 1 || key.getKey() == null) {
            throw new DataIntegrityViolationException("Khong nhan duoc ID cua offer vua tao");
        }
        return new AssignmentOffer(key.getKey().longValue(), orderId, driverId, storedDistance, offeredAt, expiresAt);
    }

    record OrderSnapshot(long id, long restaurantId, Long driverId, String status) {
        boolean acceptsDriver() {
            return "PLACED".equals(status) || "PREPARING".equals(status) || "READY_FOR_PICKUP".equals(status);
        }

        boolean canAssign() {
            return acceptsDriver() && driverId == null;
        }
    }

    record RestaurantLocation(long id, BigDecimal lat, BigDecimal lng) {
    }

    record InvitationHistory(boolean previouslyInvited, boolean rejected, boolean missingExpiry,
                             LocalDateTime latestExpiredAt) {
        static final InvitationHistory NEVER_INVITED = new InvitationHistory(false, false, false, null);

        boolean canInvite(LocalDateTime now) {
            return !rejected && !missingExpiry
                    && (latestExpiredAt == null || !latestExpiredAt.plusSeconds(60).isAfter(now));
        }
    }
}
