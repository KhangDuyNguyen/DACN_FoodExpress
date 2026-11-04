package vn.hutech.fooddelivery.assignment;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Chi doc snapshot ung vien; khong giu cho tai xe va khong tao/cap nhat offer hay don hang. */
@Repository
public class AssignmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AssignmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ben goi truyen cung now (gio Backend) cho repository va NearestDriverSelector.
     * Nguong GPS 60 giay van la de xuat, dong bo voi selector NV2.1.
     * Don da gan tai xe va chua DELIVERED/CANCELLED/REJECTED deu chan tai xe,
     * ke ca PENDING_PAYMENT; can chot lai quy tac nay voi module Order khi tich hop.
     * Moi OFFERED deu chan, ke ca expires_at da qua: dot nay khong tu suy ra EXPIRED.
     * Ket qua chi la snapshot; dot ghi offer sau nay phai kiem tra lai trong transaction.
     */
    public List<DriverCandidate> findEligibleCandidates(LocalDateTime now) {
        Objects.requireNonNull(now, "now");

        // last_location_at la DATETIME(0). Lam tron can duoi len va can tren xuong
        // de giu dung [now - 60s, now], khong mat bien nano giay khi JDBC chi gui micro giay.
        LocalDateTime latestLocationAt = now.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime oldestLocationAt = now.minusSeconds(60);
        if (oldestLocationAt.getNano() != 0) {
            oldestLocationAt = oldestLocationAt.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        }

        String sql = """
                SELECT d.id, d.status, d.is_online, d.current_lat, d.current_lng, d.last_location_at
                FROM drivers d
                WHERE d.status = 'APPROVED'
                  AND d.is_online = TRUE
                  AND d.current_lat BETWEEN -90 AND 90
                  AND d.current_lng BETWEEN -180 AND 180
                  AND d.last_location_at BETWEEN :oldestLocationAt AND :latestLocationAt
                  AND NOT EXISTS (
                      SELECT 1 FROM orders o
                      WHERE o.driver_id = d.id
                        AND o.status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM order_assignments oa
                      WHERE oa.driver_id = d.id AND oa.status = 'OFFERED'
                  )
                ORDER BY d.id
                """;
        var params = new MapSqlParameterSource()
                .addValue("oldestLocationAt", Timestamp.valueOf(oldestLocationAt))
                .addValue("latestLocationAt", Timestamp.valueOf(latestLocationAt));
        return jdbc.query(sql, params, (rs, rowNum) -> new DriverCandidate(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getBoolean("is_online"),
                rs.getDouble("current_lat"),
                rs.getDouble("current_lng"),
                rs.getTimestamp("last_location_at").toLocalDateTime(),
                // Hai NOT EXISTS da loai don dang hoat dong va offer dang cho trong cung truy van.
                false,
                false
        ));
    }
}
