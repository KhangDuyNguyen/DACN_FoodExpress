package vn.hutech.fooddelivery.admin;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.admin.dto.AdminMerchantResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository quản lý truy vấn SQL thuần cho chủ quán (merchants).
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public class AdminMerchantRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<AdminMerchantResponse> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AdminMerchantResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return new AdminMerchantResponse(
                    rs.getLong("id"),
                    rs.getLong("owner_user_id"),
                    rs.getString("owner_full_name"),
                    rs.getString("owner_email"),
                    rs.getString("owner_phone"),
                    rs.getString("business_name"),
                    rs.getString("tax_code"),
                    rs.getString("contact_phone"),
                    rs.getString("status"),
                    rs.getInt("total_restaurants"),
                    createdAt != null ? createdAt.toLocalDateTime() : null,
                    updatedAt != null ? updatedAt.toLocalDateTime() : null
            );
        }
    };

    public AdminMerchantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminMerchantResponse> findMerchants(String keyword, String status, int offset, int limit) {
        String sql = """
                SELECT 
                    m.id,
                    m.owner_user_id,
                    u.full_name AS owner_full_name,
                    u.email AS owner_email,
                    u.phone AS owner_phone,
                    m.business_name,
                    m.tax_code,
                    m.contact_phone,
                    m.status,
                    (SELECT COUNT(*) FROM restaurants r WHERE r.merchant_id = m.id) AS total_restaurants,
                    m.created_at,
                    m.updated_at
                FROM merchants m
                JOIN users u ON u.id = m.owner_user_id
                WHERE (:keyword IS NULL OR (
                    m.business_name LIKE CONCAT('%', :keyword, '%')
                    OR u.full_name LIKE CONCAT('%', :keyword, '%')
                    OR u.phone LIKE CONCAT('%', :keyword, '%')
                    OR m.contact_phone LIKE CONCAT('%', :keyword, '%')
                ))
                AND (:status IS NULL OR m.status = :status)
                ORDER BY m.created_at DESC
                LIMIT :limit OFFSET :offset
                """;
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim())
                .addValue("status", status == null || status.isBlank() ? null : status.trim().toUpperCase())
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(sql, params, ROW_MAPPER);
    }

    public long countMerchants(String keyword, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM merchants m
                JOIN users u ON u.id = m.owner_user_id
                WHERE (:keyword IS NULL OR (
                    m.business_name LIKE CONCAT('%', :keyword, '%')
                    OR u.full_name LIKE CONCAT('%', :keyword, '%')
                    OR u.phone LIKE CONCAT('%', :keyword, '%')
                    OR m.contact_phone LIKE CONCAT('%', :keyword, '%')
                ))
                AND (:status IS NULL OR m.status = :status)
                """;
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim())
                .addValue("status", status == null || status.isBlank() ? null : status.trim().toUpperCase());

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    public Optional<AdminMerchantResponse> findById(long id) {
        String sql = """
                SELECT 
                    m.id,
                    m.owner_user_id,
                    u.full_name AS owner_full_name,
                    u.email AS owner_email,
                    u.phone AS owner_phone,
                    m.business_name,
                    m.tax_code,
                    m.contact_phone,
                    m.status,
                    (SELECT COUNT(*) FROM restaurants r WHERE r.merchant_id = m.id) AS total_restaurants,
                    m.created_at,
                    m.updated_at
                FROM merchants m
                JOIN users u ON u.id = m.owner_user_id
                WHERE m.id = :id
                """;
        List<AdminMerchantResponse> results = jdbc.query(sql, Map.of("id", id), ROW_MAPPER);
        return results.stream().findFirst();
    }

    public Optional<AdminMerchantResponse> findByOwnerUserId(long ownerUserId) {
        String sql = """
                SELECT 
                    m.id,
                    m.owner_user_id,
                    u.full_name AS owner_full_name,
                    u.email AS owner_email,
                    u.phone AS owner_phone,
                    m.business_name,
                    m.tax_code,
                    m.contact_phone,
                    m.status,
                    (SELECT COUNT(*) FROM restaurants r WHERE r.merchant_id = m.id) AS total_restaurants,
                    m.created_at,
                    m.updated_at
                FROM merchants m
                JOIN users u ON u.id = m.owner_user_id
                WHERE m.owner_user_id = :ownerUserId
                """;
        List<AdminMerchantResponse> results = jdbc.query(sql, Map.of("ownerUserId", ownerUserId), ROW_MAPPER);
        return results.stream().findFirst();
    }

    public int updateStatus(long id, String status) {
        String sql = """
                UPDATE merchants
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """;
        return jdbc.update(sql, Map.of("id", id, "status", status));
    }

    public void insert(long ownerUserId, String businessName, String contactPhone) {
        String sql = """
                INSERT INTO merchants (owner_user_id, business_name, contact_phone, status)
                VALUES (:ownerUserId, :businessName, :contactPhone, 'PENDING')
                """;
        var params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("businessName", businessName)
                .addValue("contactPhone", contactPhone);
        jdbc.update(sql, params);
    }
}

