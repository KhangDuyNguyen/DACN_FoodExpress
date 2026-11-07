package vn.hutech.fooddelivery.admin;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.admin.dto.AdminRestaurantResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository quản lý truy vấn SQL thuần cho nhà hàng (restaurants).
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public class AdminRestaurantRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<AdminRestaurantResponse> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AdminRestaurantResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return new AdminRestaurantResponse(
                    rs.getLong("id"),
                    rs.getLong("merchant_id"),
                    rs.getString("merchant_business_name"),
                    rs.getLong("merchant_owner_user_id"),
                    rs.getString("merchant_owner_name"),
                    rs.getString("merchant_owner_phone"),
                    rs.getString("merchant_status"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("phone"),
                    rs.getString("address"),
                    rs.getBigDecimal("lat"),
                    rs.getBigDecimal("lng"),
                    rs.getString("logo_url"),
                    rs.getString("cover_url"),
                    rs.getString("open_status"),
                    rs.getString("status"),
                    rs.getBigDecimal("min_order_amount"),
                    rs.getInt("prep_time_minutes"),
                    rs.getBigDecimal("avg_rating"),
                    rs.getInt("rating_count"),
                    createdAt != null ? createdAt.toLocalDateTime() : null,
                    updatedAt != null ? updatedAt.toLocalDateTime() : null
            );
        }
    };

    public AdminRestaurantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminRestaurantResponse> findRestaurants(String keyword, String status, int offset, int limit) {
        String sql = """
                SELECT 
                    r.id,
                    r.merchant_id,
                    m.business_name AS merchant_business_name,
                    m.owner_user_id AS merchant_owner_user_id,
                    u.full_name AS merchant_owner_name,
                    u.phone AS merchant_owner_phone,
                    m.status AS merchant_status,
                    r.name,
                    r.description,
                    r.phone,
                    r.address,
                    r.lat,
                    r.lng,
                    r.logo_url,
                    r.cover_url,
                    r.open_status,
                    r.status,
                    r.min_order_amount,
                    r.prep_time_minutes,
                    r.avg_rating,
                    r.rating_count,
                    r.created_at,
                    r.updated_at
                FROM restaurants r
                JOIN merchants m ON m.id = r.merchant_id
                JOIN users u ON u.id = m.owner_user_id
                WHERE (:keyword IS NULL OR (
                    r.name LIKE CONCAT('%', :keyword, '%')
                    OR r.address LIKE CONCAT('%', :keyword, '%')
                    OR m.business_name LIKE CONCAT('%', :keyword, '%')
                    OR u.full_name LIKE CONCAT('%', :keyword, '%')
                ))
                AND (:status IS NULL OR r.status = :status)
                ORDER BY r.created_at DESC
                LIMIT :limit OFFSET :offset
                """;
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim())
                .addValue("status", status == null || status.isBlank() ? null : status.trim().toUpperCase())
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(sql, params, ROW_MAPPER);
    }

    public long countRestaurants(String keyword, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM restaurants r
                JOIN merchants m ON m.id = r.merchant_id
                JOIN users u ON u.id = m.owner_user_id
                WHERE (:keyword IS NULL OR (
                    r.name LIKE CONCAT('%', :keyword, '%')
                    OR r.address LIKE CONCAT('%', :keyword, '%')
                    OR m.business_name LIKE CONCAT('%', :keyword, '%')
                    OR u.full_name LIKE CONCAT('%', :keyword, '%')
                ))
                AND (:status IS NULL OR r.status = :status)
                """;
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim())
                .addValue("status", status == null || status.isBlank() ? null : status.trim().toUpperCase());

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    public Optional<AdminRestaurantResponse> findById(long id) {
        String sql = """
                SELECT 
                    r.id,
                    r.merchant_id,
                    m.business_name AS merchant_business_name,
                    m.owner_user_id AS merchant_owner_user_id,
                    u.full_name AS merchant_owner_name,
                    u.phone AS merchant_owner_phone,
                    m.status AS merchant_status,
                    r.name,
                    r.description,
                    r.phone,
                    r.address,
                    r.lat,
                    r.lng,
                    r.logo_url,
                    r.cover_url,
                    r.open_status,
                    r.status,
                    r.min_order_amount,
                    r.prep_time_minutes,
                    r.avg_rating,
                    r.rating_count,
                    r.created_at,
                    r.updated_at
                FROM restaurants r
                JOIN merchants m ON m.id = r.merchant_id
                JOIN users u ON u.id = m.owner_user_id
                WHERE r.id = :id
                """;
        List<AdminRestaurantResponse> results = jdbc.query(sql, Map.of("id", id), ROW_MAPPER);
        return results.stream().findFirst();
    }

    public int updateStatus(long id, String status) {
        String sql = """
                UPDATE restaurants
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """;
        return jdbc.update(sql, Map.of("id", id, "status", status));
    }
}

