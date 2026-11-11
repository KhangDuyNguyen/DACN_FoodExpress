package vn.hutech.fooddelivery.admin.dashboard.dto;

import java.math.BigDecimal;

/**
 * DTO phản hồi các chỉ số thống kê tổng quan hệ thống cho Admin Dashboard.
 */
public record AdminDashboardStatsResponse(
        long totalOrders,
        BigDecimal totalRevenue,
        long totalUsers,
        long totalRestaurants
) {
}
