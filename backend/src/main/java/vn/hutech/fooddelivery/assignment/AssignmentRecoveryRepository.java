package vn.hutech.fooddelivery.assignment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Chi quet ID co gioi han. Viec ghi phai qua transaction Order -> Driver -> Offer. */
@Repository
public class AssignmentRecoveryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AssignmentRecoveryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Long> findRecoverableOfferIds(long afterId, int limit, LocalDateTime now) {
        return jdbc.query("""
                SELECT oa.id FROM order_assignments oa JOIN orders o ON o.id = oa.order_id
                WHERE oa.id > :afterId AND oa.status = 'OFFERED'
                  AND (oa.expires_at IS NULL OR oa.expires_at <= :now
                       OR o.status NOT IN ('PLACED', 'PREPARING', 'READY_FOR_PICKUP') OR o.driver_id IS NOT NULL)
                ORDER BY oa.id LIMIT :limit
                """, scanParameters(afterId, limit, now), (rs, i) -> rs.getLong("id"));
    }

    List<Long> findWaitingOrderIds(long afterId, int limit, LocalDateTime now) {
        return jdbc.query("""
                SELECT o.id FROM orders o
                WHERE o.id > :afterId AND o.status IN ('PLACED', 'PREPARING', 'READY_FOR_PICKUP') AND o.driver_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM order_assignments oa WHERE oa.order_id = o.id AND oa.status = 'OFFERED'
                        AND oa.offered_at <= :now AND oa.expires_at > :now
                  )
                ORDER BY o.id LIMIT :limit
                """, scanParameters(afterId, limit, now), (rs, i) -> rs.getLong("id"));
    }

    List<Long> findOfferedIdsForOrder(long orderId, int limit) {
        return jdbc.query("""
                SELECT id FROM order_assignments WHERE order_id = :orderId AND status = 'OFFERED'
                ORDER BY id LIMIT :limit
                """, Map.of("orderId", orderId, "limit", limit), (rs, i) -> rs.getLong("id"));
    }

    boolean isWaitingWithoutOffer(long orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM orders o WHERE o.id = :id
                      AND o.status IN ('PLACED', 'PREPARING', 'READY_FOR_PICKUP') AND o.driver_id IS NULL
                      AND NOT EXISTS (SELECT 1 FROM order_assignments oa
                                      WHERE oa.order_id = o.id AND oa.status = 'OFFERED')
                )
                """, Map.of("id", orderId), Boolean.class));
    }

    void completeOffer(DriverOfferRepository.OfferSnapshot offer, String status, LocalDateTime now) {
        if (!"EXPIRED".equals(status) && !"CANCELLED".equals(status)) {
            throw new IllegalArgumentException("Recovery chi duoc chuyen EXPIRED/CANCELLED");
        }
        var params = new MapSqlParameterSource().addValue("id", offer.id()).addValue("orderId", offer.orderId())
                .addValue("driverId", offer.driverId()).addValue("status", status)
                .addValue("now", Timestamp.valueOf(now.truncatedTo(ChronoUnit.SECONDS)));
        int updated = jdbc.update("""
                UPDATE order_assignments
                SET status = :status, responded_at = :now,
                    expires_at = CASE WHEN :status = 'EXPIRED' THEN COALESCE(expires_at, :now) ELSE expires_at END
                WHERE id = :id AND order_id = :orderId AND driver_id = :driverId AND status = 'OFFERED'
                  AND (:status = 'CANCELLED' OR expires_at IS NULL OR expires_at <= :now)
                """, params);
        if (updated != 1) throw new DataIntegrityViolationException("Khong cap nhat duoc offer da khoa khi recovery");
    }

    private static MapSqlParameterSource scanParameters(long afterId, int limit, LocalDateTime now) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Batch phai tu 1 den 50");
        // DATETIME(0): floor(now) giu dung phep so sanh, khong lam tron nano giay qua expires_at.
        return new MapSqlParameterSource().addValue("afterId", afterId).addValue("limit", limit)
                .addValue("now", Timestamp.valueOf(now.truncatedTo(ChronoUnit.SECONDS)));
    }
}
