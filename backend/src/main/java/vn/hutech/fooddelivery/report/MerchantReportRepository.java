package vn.hutech.fooddelivery.report;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.report.dto.TopItemReportItem;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository truy vấn báo cáo doanh thu và món bán chạy bằng NamedParameterJdbcTemplate (SQL thuần).
 */
@Repository
public class MerchantReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MerchantReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Kiểm tra quyền sở hữu nhà hàng:
     * Quán phải thuộc merchant do user_id hiện tại làm chủ (merchants.owner_user_id).
     */
    public boolean isRestaurantOwner(long restaurantId, long userId) {
        String sql = """
                SELECT COUNT(*)
                FROM restaurants r
                JOIN merchants m ON m.id = r.merchant_id
                WHERE r.id = :restaurantId AND m.owner_user_id = :userId
                """;
        var params = new MapSqlParameterSource()
                .addValue("restaurantId", restaurantId)
                .addValue("userId", userId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public record DailyStat(LocalDate date, BigDecimal revenue, long orderCount) {
    }

    /**
     * Thống kê doanh thu và số lượng đơn hàng theo từng ngày có phát sinh đơn DELIVERED trong khoảng [from, to].
     */
    public Map<LocalDate, DailyStat> getDailyRevenueStats(long restaurantId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT 
                    DATE(created_at) AS report_date,
                    COALESCE(SUM(total_amount), 0) AS revenue,
                    COUNT(*) AS order_count
                FROM orders
                WHERE restaurant_id = :restaurantId
                  AND status = 'DELIVERED'
                  AND created_at >= :fromStart
                  AND created_at < :toEnd
                GROUP BY DATE(created_at)
                ORDER BY report_date ASC
                """;
        var params = new MapSqlParameterSource()
                .addValue("restaurantId", restaurantId)
                .addValue("fromStart", Timestamp.valueOf(from.atStartOfDay()))
                .addValue("toEnd", Timestamp.valueOf(to.plusDays(1).atStartOfDay()));

        List<DailyStat> list = jdbc.query(sql, params, (rs, rowNum) -> {
            java.sql.Date d = rs.getDate("report_date");
            LocalDate date = d != null ? d.toLocalDate() : null;
            BigDecimal revenue = Optional.ofNullable(rs.getBigDecimal("revenue")).orElse(BigDecimal.ZERO);
            long count = rs.getLong("order_count");
            return new DailyStat(date, revenue, count);
        });

        Map<LocalDate, DailyStat> map = new HashMap<>();
        for (DailyStat s : list) {
            if (s.date() != null) {
                map.put(s.date(), s);
            }
        }
        return map;
    }

    /**
     * Thống kê các món bán chạy nhất trong khoảng [from, to] của đơn hàng DELIVERED.
     * Sắp xếp theo số lượng bán giảm dần (quantitySold DESC) và giới hạn số lượng (limit).
     */
    public List<TopItemReportItem> getTopSellingItems(long restaurantId, LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT 
                    oi.menu_item_id AS item_id,
                    oi.item_name AS item_name,
                    COALESCE(SUM(oi.quantity), 0) AS quantity_sold,
                    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.restaurant_id = :restaurantId
                  AND o.status = 'DELIVERED'
                  AND o.created_at >= :fromStart
                  AND o.created_at < :toEnd
                GROUP BY oi.menu_item_id, oi.item_name
                ORDER BY quantity_sold DESC, revenue DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("restaurantId", restaurantId)
                .addValue("fromStart", Timestamp.valueOf(from.atStartOfDay()))
                .addValue("toEnd", Timestamp.valueOf(to.plusDays(1).atStartOfDay()))
                .addValue("limit", limit);

        return jdbc.query(sql, params, (rs, rowNum) -> {
            Long itemId = rs.getObject("item_id") != null ? rs.getLong("item_id") : null;
            String itemName = rs.getString("item_name");
            long quantitySold = rs.getLong("quantity_sold");
            BigDecimal revenue = Optional.ofNullable(rs.getBigDecimal("revenue")).orElse(BigDecimal.ZERO);
            return new TopItemReportItem(itemId, itemName, quantitySold, revenue);
        });
    }
}
