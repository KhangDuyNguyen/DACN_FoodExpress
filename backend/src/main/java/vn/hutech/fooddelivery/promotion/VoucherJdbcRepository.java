package vn.hutech.fooddelivery.promotion;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.promotion.dto.MerchantRestaurantOption;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository truy xuất bảng vouchers và voucher_usages bằng SQL thuần với NamedParameterJdbcTemplate.
 * Tuân thủ 100% quy ước nhóm trong docs/CONVENTIONS.md:
 * "Luồng chuẩn: Controller -> Service -> Repository (SQL thuần) -> MySQL".
 * Không sử dụng JPA.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public class VoucherJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public VoucherJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static final RowMapper<VoucherRow> ROW_MAPPER = new RowMapper<>() {
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
            WHERE UPPER(code) = UPPER(:code)
            """;
        List<VoucherRow> list = jdbc.query(sql, Map.of("code", code.trim()), ROW_MAPPER);
        return list.stream().findFirst();
    }

    /**
     * Kiểm tra mã code đã tồn tại chưa (trừ ID hiện tại nếu là update).
     */
    public boolean existsByCode(String code, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM vouchers WHERE UPPER(code) = UPPER(:code)";
        MapSqlParameterSource params = new MapSqlParameterSource("code", code.trim());
        if (excludeId != null) {
            sql += " AND id != :excludeId";
            params.addValue("excludeId", excludeId);
        }
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    /**
     * Lấy danh sách các voucher khả dụng cho giỏ hàng:
     * - ACTIVE
     * - Trong khoảng thời gian hiệu lực
     * - Chưa vượt quá usage_limit tổng
     * - Đạt giá trị đơn hàng tối thiểu
     * - Áp dụng toàn sàn (restaurant_id IS NULL) hoặc áp dụng cho đúng nhà hàng này.
     */
    public List<VoucherRow> findAvailableVouchers(LocalDateTime now, Long restaurantId, BigDecimal orderAmount) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, code, title, description, discount_type, discount_value,
                   min_order, max_discount, start_at, end_at, usage_limit,
                   per_user_limit, used_count, restaurant_id, created_by, status, created_at
            FROM vouchers
            WHERE status = 'ACTIVE'
              AND :now BETWEEN start_at AND end_at
              AND (usage_limit IS NULL OR used_count < usage_limit)
              AND min_order <= :orderAmount
            """);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("now", now);
        params.addValue("orderAmount", orderAmount != null ? orderAmount : BigDecimal.ZERO);

        if (restaurantId != null) {
            sql.append(" AND (restaurant_id IS NULL OR restaurant_id = :restaurantId)");
            params.addValue("restaurantId", restaurantId);
        } else {
            sql.append(" AND restaurant_id IS NULL");
        }

        sql.append(" ORDER BY created_at DESC");

        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    /**
     * Đếm số lượt người dùng cụ thể đã sử dụng voucher này.
     */
    public int countUserUsage(Long voucherId, Long userId) {
        String sql = "SELECT COUNT(*) FROM voucher_usages WHERE voucher_id = :voucherId AND user_id = :userId";
        Integer count = jdbc.queryForObject(sql, Map.of("voucherId", voucherId, "userId", userId), Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Tạo mới voucher bằng SQL thuần.
     */
    public Long create(Voucher voucher) {
        String sql = """
            INSERT INTO vouchers (code, title, description, discount_type, discount_value,
                                 min_order, max_discount, start_at, end_at, usage_limit,
                                 per_user_limit, used_count, restaurant_id, created_by, status, created_at)
            VALUES (:code, :title, :description, :discountType, :discountValue,
                    :minOrder, :maxDiscount, :startAt, :endAt, :usageLimit,
                    :perUserLimit, 0, :restaurantId, :createdBy, :status, :createdAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", voucher.getCode().toUpperCase().trim())
                .addValue("title", voucher.getTitle())
                .addValue("description", voucher.getDescription())
                .addValue("discountType", voucher.getDiscountType().name())
                .addValue("discountValue", voucher.getDiscountValue())
                .addValue("minOrder", voucher.getMinOrder())
                .addValue("maxDiscount", voucher.getMaxDiscount())
                .addValue("startAt", voucher.getStartAt())
                .addValue("endAt", voucher.getEndAt())
                .addValue("usageLimit", voucher.getUsageLimit())
                .addValue("perUserLimit", voucher.getPerUserLimit() != null ? voucher.getPerUserLimit() : 1)
                .addValue("restaurantId", voucher.getRestaurantId())
                .addValue("createdBy", voucher.getCreatedBy())
                .addValue("status", voucher.getStatus() != null ? voucher.getStatus().name() : "ACTIVE")
                .addValue("createdAt", voucher.getCreatedAt() != null ? voucher.getCreatedAt() : LocalDateTime.now());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    /**
     * Cập nhật thông tin voucher bằng SQL thuần.
     */
    public int update(Voucher voucher) {
        String sql = """
            UPDATE vouchers
            SET title = :title,
                description = :description,
                discount_type = :discountType,
                discount_value = :discountValue,
                min_order = :minOrder,
                max_discount = :maxDiscount,
                start_at = :startAt,
                end_at = :endAt,
                usage_limit = :usageLimit,
                per_user_limit = :perUserLimit,
                status = :status
            WHERE id = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", voucher.getId())
                .addValue("title", voucher.getTitle())
                .addValue("description", voucher.getDescription())
                .addValue("discountType", voucher.getDiscountType().name())
                .addValue("discountValue", voucher.getDiscountValue())
                .addValue("minOrder", voucher.getMinOrder())
                .addValue("maxDiscount", voucher.getMaxDiscount())
                .addValue("startAt", voucher.getStartAt())
                .addValue("endAt", voucher.getEndAt())
                .addValue("usageLimit", voucher.getUsageLimit())
                .addValue("perUserLimit", voucher.getPerUserLimit())
                .addValue("status", voucher.getStatus().name());

        return jdbc.update(sql, params);
    }

    /**
     * Thay đổi trạng thái voucher (ACTIVE, PAUSED, EXPIRED).
     */
    public int updateStatus(Long id, VoucherStatus status) {
        String sql = "UPDATE vouchers SET status = :status WHERE id = :id";
        return jdbc.update(sql, Map.of("id", id, "status", status.name()));
    }

    /**
     * Tăng số lượt sử dụng voucher nguyên tử (Atomic increment), ngăn chặn race condition.
     * Trả về 1 nếu thành công, 0 nếu không tìm thấy hoặc đã hết lượt.
     */
    public int incrementUsedCount(Long voucherId) {
        String sql = """
            UPDATE vouchers
            SET used_count = used_count + 1
            WHERE id = :id
              AND (usage_limit IS NULL OR used_count < usage_limit)
            """;
        return jdbc.update(sql, Map.of("id", voucherId));
    }

    /**
     * Ghi nhận lịch sử sử dụng voucher của người dùng cho đơn hàng.
     */
    public void recordUsage(Long voucherId, Long userId, Long orderId, LocalDateTime usedAt) {
        String sql = """
            INSERT INTO voucher_usages (voucher_id, user_id, order_id, used_at)
            VALUES (:voucherId, :userId, :orderId, :usedAt)
            """;
        jdbc.update(sql, Map.of(
                "voucherId", voucherId,
                "userId", userId,
                "orderId", orderId,
                "usedAt", usedAt != null ? usedAt : LocalDateTime.now()
        ));
    }

    /**
     * Danh sách voucher cho Admin (Toàn sàn hoặc tất cả), hỗ trợ tìm kiếm và phân trang.
     */
    public List<VoucherRow> findForAdmin(String keyword, String discountType, String status, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, code, title, description, discount_type, discount_value,
                   min_order, max_discount, start_at, end_at, usage_limit,
                   per_user_limit, used_count, restaurant_id, created_by, status, created_at
            FROM vouchers
            WHERE 1=1
            """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (UPPER(code) LIKE UPPER(:keyword) OR UPPER(title) LIKE UPPER(:keyword))");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (discountType != null && !discountType.isBlank()) {
            sql.append(" AND discount_type = :discountType");
            params.addValue("discountType", discountType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }

        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    /**
     * Đếm tổng số voucher theo điều kiện lọc cho Admin.
     */
    public long countForAdmin(String keyword, String discountType, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM vouchers WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (UPPER(code) LIKE UPPER(:keyword) OR UPPER(title) LIKE UPPER(:keyword))");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (discountType != null && !discountType.isBlank()) {
            sql.append(" AND discount_type = :discountType");
            params.addValue("discountType", discountType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }

        Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
        return total != null ? total : 0;
    }

    /**
     * Danh sách voucher thuộc các quán ăn của Merchant.
     */
    public List<VoucherRow> findForMerchant(List<Long> restaurantIds, String keyword, String status, int offset, int limit) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder("""
            SELECT id, code, title, description, discount_type, discount_value,
                   min_order, max_discount, start_at, end_at, usage_limit,
                   per_user_limit, used_count, restaurant_id, created_by, status, created_at
            FROM vouchers
            WHERE restaurant_id IN (:restaurantIds)
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("restaurantIds", restaurantIds);

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (UPPER(code) LIKE UPPER(:keyword) OR UPPER(title) LIKE UPPER(:keyword))");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }

        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    /**
     * Đếm tổng số voucher của Merchant.
     */
    public long countForMerchant(List<Long> restaurantIds, String keyword, String status) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM vouchers WHERE restaurant_id IN (:restaurantIds)");
        MapSqlParameterSource params = new MapSqlParameterSource("restaurantIds", restaurantIds);

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (UPPER(code) LIKE UPPER(:keyword) OR UPPER(title) LIKE UPPER(:keyword))");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }

        Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
        return total != null ? total : 0;
    }

    /**
     * Lấy danh sách ID các quán ăn thuộc sở hữu của tài khoản Merchant (users.id).
     */
    public List<Long> findRestaurantIdsByOwnerUserId(Long ownerUserId) {
        String sql = """
            SELECT r.id
            FROM restaurants r
            JOIN merchants m ON r.merchant_id = m.id
            WHERE m.owner_user_id = :ownerUserId
            """;
        return jdbc.queryForList(sql, Map.of("ownerUserId", ownerUserId), Long.class);
    }

    /**
     * Lấy thông tin các quán ăn thuộc sở hữu của Merchant cho dropdown lựa chọn.
     */
    public List<MerchantRestaurantOption> findRestaurantsByOwnerUserId(Long ownerUserId) {
        String sql = """
            SELECT r.id, r.name, r.address
            FROM restaurants r
            JOIN merchants m ON r.merchant_id = m.id
            WHERE m.owner_user_id = :ownerUserId
            ORDER BY r.name ASC
            """;
        return jdbc.query(sql, Map.of("ownerUserId", ownerUserId), (rs, i) -> new MerchantRestaurantOption(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("address")
        ));
    }

    /**
     * Kiểm tra một quán ăn có thuộc quyền sở hữu của chủ tài khoản này không.
     */
    public boolean isRestaurantOwnedByUser(Long restaurantId, Long ownerUserId) {
        String sql = """
            SELECT COUNT(*)
            FROM restaurants r
            JOIN merchants m ON r.merchant_id = m.id
            WHERE r.id = :restaurantId AND m.owner_user_id = :ownerUserId
            """;
        Integer count = jdbc.queryForObject(sql, Map.of("restaurantId", restaurantId, "ownerUserId", ownerUserId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Lấy tên nhà hàng theo danh sách ID để hiển thị trên giao diện.
     */
    public Map<Long, String> getRestaurantNamesByIds(Collection<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT id, name FROM restaurants WHERE id IN (:ids)";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", restaurantIds);
        Map<Long, String> result = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getLong("id"), rs.getString("name"));
        });
        return result;
    }
}
