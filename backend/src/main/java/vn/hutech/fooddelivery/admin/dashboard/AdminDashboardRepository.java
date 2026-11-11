package vn.hutech.fooddelivery.admin.dashboard;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.admin.dashboard.dto.AdminDashboardStatsResponse;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Repository truy vấn thống kê dữ liệu cho Admin Dashboard bằng NamedParameterJdbcTemplate.
 */
@Repository
public class AdminDashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AdminDashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lấy các chỉ số thống kê tổng quan:
     * - totalOrders: Tổng số đơn hàng đã đặt trên toàn hệ thống (không tính đơn đang PENDING_PAYMENT)
     * - totalRevenue: Tổng doanh thu từ các đơn hàng đã hoàn thành giao hàng (status = 'DELIVERED')
     * - totalUsers: Tổng số người dùng đang hoạt động / chưa bị xóa (status <> 'DELETED')
     * - totalRestaurants: Tổng số nhà hàng trong hệ thống
     */
    public AdminDashboardStatsResponse getStats() {
        String sql = """
                SELECT 
                    (SELECT COUNT(*) FROM orders WHERE status <> 'PENDING_PAYMENT') AS total_orders,
                    (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status = 'DELIVERED') AS total_revenue,
                    (SELECT COUNT(*) FROM users WHERE status <> 'DELETED') AS total_users,
                    (SELECT COUNT(*) FROM restaurants) AS total_restaurants
                """;

        AdminDashboardStatsResponse stats = jdbc.queryForObject(sql, Map.of(), (rs, rowNum) -> new AdminDashboardStatsResponse(
                rs.getLong("total_orders"),
                Optional.ofNullable(rs.getBigDecimal("total_revenue")).orElse(BigDecimal.ZERO),
                rs.getLong("total_users"),
                rs.getLong("total_restaurants")
        ));

        return stats != null ? stats : new AdminDashboardStatsResponse(0L, BigDecimal.ZERO, 0L, 0L);
    }
}
