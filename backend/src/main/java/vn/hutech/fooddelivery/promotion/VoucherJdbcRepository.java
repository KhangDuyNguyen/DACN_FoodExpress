package vn.hutech.fooddelivery.promotion;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository truy xuất bảng vouchers bằng SQL thuần với NamedParameterJdbcTemplate.
 * Phù hợp 100% với quy ước của nhóm trong docs/CONVENTIONS.md:
 * "Luồng chuẩn: Controller -> Service -> Repository (SQL thuần) -> MySQL".
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public class VoucherJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public VoucherJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<VoucherRow> ROW_MAPPER = new RowMapper<>() {
        @Override
        public VoucherRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new VoucherRow(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("discount_type"),
                    rs.getBigDecimal("discount_value"),
                    rs.getBigDecimal("min_order"),
                    rs.getBigDecimal("max_discount"),
                    rs.getObject("start_at", LocalDateTime.class),
                    rs.getObject("end_at", LocalDateTime.class),
                    rs.getObject("usage_limit") != null ? rs.getInt("usage_limit") : null,
                    rs.getInt("per_user_limit"),
                    rs.getInt("used_count"),
                    rs.getObject("restaurant_id") != null ? rs.getLong("restaurant_id") : null,
                    rs.getObject("created_by") != null ? rs.getLong("created_by") : null,
                    rs.getString("status"),
                    rs.getObject("created_at", LocalDateTime.class)
            );
        }
    };

    /**
     * Tìm voucher theo ID bằng SQL thuần.
     */
    public Optional<VoucherRow> findById(Long id) {
        String sql = """
            SELECT id, code, title, description, discount_type, discount_value,
                   min_order, max_discount, start_at, end_at, usage_limit,
                   per_user_limit, used_count, restaurant_id, created_by, status, created_at
            FROM vouchers
            WHERE id = :id
            """;
        List<VoucherRow> list = jdbc.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.stream().findFirst();
    }

    /**
     * Tìm voucher theo mã Code bằng SQL thuần.
     */
    public Optional<VoucherRow> findByCode(String code) {
        String sql = """
            SELECT id, code, title, description, discount_type, discount_value,
                   min_order, max_discount, start_at, end_at, usage_limit,
                   per_user_limit, used_count, restaurant_id, created_by, status, created_at
            FROM vouchers
            WHERE code = :code
            """;
        List<VoucherRow> list = jdbc.query(sql, Map.of("code", code), ROW_MAPPER);
        return list.stream().findFirst();
    }

    /**
     * Lấy danh sách các voucher đang ACTIVE và còn trong thời hạn sử dụng.
     */
    public List<VoucherRow> findActiveVouchers(LocalDateTime now) {
        String sql = """
            SELECT id, code, title, description, discount_type, discount_value,
                   min_order, max_discount, start_at, end_at, usage_limit,
                   per_user_limit, used_count, restaurant_id, created_by, status, created_at
            FROM vouchers
            WHERE status = 'ACTIVE'
              AND :now BETWEEN start_at AND end_at
              AND (usage_limit IS NULL OR used_count < usage_limit)
            ORDER BY created_at DESC
            """;
        return jdbc.query(sql, Map.of("now", now), ROW_MAPPER);
    }
}
