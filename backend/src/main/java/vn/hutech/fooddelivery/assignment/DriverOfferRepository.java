package vn.hutech.fooddelivery.assignment;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.common.ConflictException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DriverOfferRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DriverOfferRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Long> findDriverId(long userId) {
        return jdbc.query("SELECT id FROM drivers WHERE user_id = :userId", Map.of("userId", userId),
                (rs, i) -> rs.getLong("id")).stream().findFirst();
    }

    List<DriverOffersResponse.Offer> findActiveOffers(long driverId, LocalDateTime now) {
        String sql = """
                SELECT oa.id AS offer_id, o.id AS order_id, o.code AS order_code,
                       r.name AS restaurant_name, r.address AS restaurant_address,
                       r.lat AS pickup_lat, r.lng AS pickup_lng, o.delivery_address,
                       oa.distance_km, oa.offered_at, oa.expires_at
                FROM order_assignments oa
                JOIN orders o ON o.id = oa.order_id
                JOIN restaurants r ON r.id = o.restaurant_id
                WHERE oa.driver_id = :driverId AND oa.status = 'OFFERED'
                  AND oa.offered_at <= :now AND oa.expires_at > :now
                  AND o.status IN ('PLACED', 'PREPARING', 'READY_FOR_PICKUP') AND o.driver_id IS NULL
                ORDER BY oa.offered_at, oa.id
                """;
        // Cot DATETIME(0): so sanh voi floor(now) tuong duong chinh xac, tranh JDBC lam tron nano -> micro.
        var params = new MapSqlParameterSource().addValue("driverId", driverId)
                .addValue("now", Timestamp.valueOf(now.truncatedTo(ChronoUnit.SECONDS)));
        return jdbc.query(sql, params, (rs, i) -> new DriverOffersResponse.Offer(
                rs.getLong("offer_id"), rs.getLong("order_id"), rs.getString("order_code"),
                rs.getString("restaurant_name"), rs.getString("restaurant_address"),
                rs.getBigDecimal("pickup_lat"), rs.getBigDecimal("pickup_lng"), rs.getString("delivery_address"),
                rs.getBigDecimal("distance_km"), time(rs, "offered_at"), time(rs, "expires_at")));
    }

    Optional<OfferMetadata> findMetadata(long offerId) {
        return jdbc.query("SELECT id, order_id, driver_id FROM order_assignments WHERE id = :id",
                Map.of("id", offerId), (rs, i) -> new OfferMetadata(rs.getLong("id"),
                        rs.getLong("order_id"), rs.getLong("driver_id"))).stream().findFirst();
    }

    Optional<DriverCandidate> lockDriverForUser(long driverId, long userId) {
        return jdbc.query("""
                SELECT id, status, is_online, current_lat, current_lng, last_location_at
                FROM drivers WHERE id = :driverId AND user_id = :userId FOR UPDATE
                """, Map.of("driverId", driverId, "userId", userId), (rs, i) -> {
            BigDecimal lat = rs.getBigDecimal("current_lat");
            BigDecimal lng = rs.getBigDecimal("current_lng");
            return new DriverCandidate(rs.getLong("id"), rs.getString("status"), rs.getBoolean("is_online"),
                    lat == null ? null : lat.doubleValue(), lng == null ? null : lng.doubleValue(),
                    time(rs, "last_location_at"), false, false);
        }).stream().findFirst();
    }

    Optional<OfferSnapshot> lockOffer(long offerId) {
        return jdbc.query("""
                SELECT id, order_id, driver_id, status, offered_at, expires_at, responded_at
                FROM order_assignments WHERE id = :id FOR UPDATE
                """, Map.of("id", offerId), (rs, i) -> new OfferSnapshot(rs.getLong("id"), rs.getLong("order_id"),
                rs.getLong("driver_id"), rs.getString("status"), time(rs, "offered_at"),
                time(rs, "expires_at"), time(rs, "responded_at"))).stream().findFirst();
    }

    boolean hasOtherOffered(long driverId, long offerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM order_assignments
                               WHERE driver_id = :driverId AND id <> :offerId AND status = 'OFFERED')
                """, Map.of("driverId", driverId, "offerId", offerId), Boolean.class));
    }

    void respond(OfferSnapshot offer, String status, LocalDateTime respondedAt) {
        int updated = jdbc.update("""
                UPDATE order_assignments SET status = :status, responded_at = :respondedAt
                WHERE id = :id AND order_id = :orderId AND driver_id = :driverId AND status = 'OFFERED'
                """, new MapSqlParameterSource().addValue("status", status)
                .addValue("respondedAt", Timestamp.valueOf(respondedAt)).addValue("id", offer.id())
                .addValue("orderId", offer.orderId()).addValue("driverId", offer.driverId()));
        if (updated != 1) throw new ConflictException("Lời mời đã thay đổi. Vui lòng tải lại");
    }

    void assignOrder(long orderId, long driverId) {
        int updated = jdbc.update("""
                UPDATE orders SET driver_id = :driverId
                WHERE id = :orderId AND status IN ('PLACED', 'PREPARING', 'READY_FOR_PICKUP') AND driver_id IS NULL
                """, Map.of("orderId", orderId, "driverId", driverId));
        if (updated != 1) throw new ConflictException("Đơn hàng không còn nhận được tài xế");
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    record OfferMetadata(long id, long orderId, long driverId) {
    }

    record OfferSnapshot(long id, long orderId, long driverId, String status,
                         LocalDateTime offeredAt, LocalDateTime expiresAt, LocalDateTime respondedAt) {
        boolean isEffective(LocalDateTime now) {
            return "OFFERED".equals(status) && offeredAt != null && !offeredAt.isAfter(now)
                    && expiresAt != null && expiresAt.isAfter(now);
        }

        DriverOfferActionResponse response() {
            return new DriverOfferActionResponse(id, orderId, status, respondedAt);
        }
    }
}
