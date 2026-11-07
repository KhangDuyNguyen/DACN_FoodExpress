package vn.hutech.fooddelivery.admin;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.admin.dto.AdminDriverResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository quản lý truy vấn SQL thuần cho tài xế (drivers).
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public class AdminDriverRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<AdminDriverResponse> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AdminDriverResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new AdminDriverResponse(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("avatar_url"),
                    rs.getString("vehicle_type"),
                    rs.getString("license_plate"),
                    rs.getString("status"),
                    rs.getBoolean("is_online"),
                    rs.getBigDecimal("current_lat"),
                    rs.getBigDecimal("current_lng"),
                    rs.getBigDecimal("avg_rating"),
                    createdAt != null ? createdAt.toLocalDateTime() : null
            );
        }
    };

    public AdminDriverRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminDriverResponse> findDrivers(String keyword, String status, int offset, int limit) {
        String sql = """
                SELECT 
                    d.id,
                    d.user_id,
                    u.full_name,
                    u.phone,
                    u.email,
                    u.avatar_url,
                    d.vehicle_type,
                    d.license_plate,
                    d.status,
                    d.is_online,
                    d.current_lat,
                    d.current_lng,
                    d.avg_rating,
                    d.created_at
                FROM drivers d
                JOIN users u ON u.id = d.user_id
                WHERE (:keyword IS NULL OR (
                    u.full_name LIKE CONCAT('%', :keyword, '%')
                    OR u.phone LIKE CONCAT('%', :keyword, '%')
                    OR d.license_plate LIKE CONCAT('%', :keyword, '%')
                ))
                AND (:status IS NULL OR d.status = :status)
                ORDER BY d.created_at DESC
                LIMIT :limit OFFSET :offset
                """;
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim())
                .addValue("status", status == null || status.isBlank() ? null : status.trim().toUpperCase())
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(sql, params, ROW_MAPPER);
    }

    public long countDrivers(String keyword, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM drivers d
                JOIN users u ON u.id = d.user_id
                WHERE (:keyword IS NULL OR (
                    u.full_name LIKE CONCAT('%', :keyword, '%')
                    OR u.phone LIKE CONCAT('%', :keyword, '%')
                    OR d.license_plate LIKE CONCAT('%', :keyword, '%')
                ))
                AND (:status IS NULL OR d.status = :status)
                """;
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim())
                .addValue("status", status == null || status.isBlank() ? null : status.trim().toUpperCase());

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    public Optional<AdminDriverResponse> findById(long id) {
        String sql = """
                SELECT 
                    d.id,
                    d.user_id,
                    u.full_name,
                    u.phone,
                    u.email,
                    u.avatar_url,
                    d.vehicle_type,
                    d.license_plate,
                    d.status,
                    d.is_online,
                    d.current_lat,
                    d.current_lng,
                    d.avg_rating,
                    d.created_at
                FROM drivers d
                JOIN users u ON u.id = d.user_id
                WHERE d.id = :id
                """;
        List<AdminDriverResponse> results = jdbc.query(sql, Map.of("id", id), ROW_MAPPER);
        return results.stream().findFirst();
    }

    public int updateStatus(long id, String status) {
        // Neu status khong phai APPROVED (vi du PENDING hoac REJECTED), dong thoi ngat online
        String sql = """
                UPDATE drivers
                SET status = :status,
                    is_online = CASE WHEN :status = 'APPROVED' THEN is_online ELSE 0 END
                WHERE id = :id
                """;
        return jdbc.update(sql, Map.of("id", id, "status", status));
    }
}

