package vn.hutech.fooddelivery.admin.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.admin.dashboard.dto.AdminDashboardStatsResponse;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminDashboardServiceTest {

    @Test
    @DisplayName("Lấy thống kê tổng quan trả về đúng số liệu từ repository")
    void getStats_success() {
        AdminDashboardStatsResponse expected = new AdminDashboardStatsResponse(
                150L,
                BigDecimal.valueOf(25000000),
                320L,
                45L
        );
        AdminDashboardRepository fakeRepo = new AdminDashboardRepository(null) {
            @Override
            public AdminDashboardStatsResponse getStats() {
                return expected;
            }
        };

        AdminDashboardService service = new AdminDashboardService(fakeRepo);
        AdminDashboardStatsResponse actual = service.getStats();

        assertNotNull(actual);
        assertEquals(150L, actual.totalOrders());
        assertEquals(BigDecimal.valueOf(25000000), actual.totalRevenue());
        assertEquals(320L, actual.totalUsers());
        assertEquals(45L, actual.totalRestaurants());
    }

    @Test
    @DisplayName("Lấy thống kê khi hệ thống chưa có dữ liệu (zero stats)")
    void getStats_emptyData() {
        AdminDashboardStatsResponse expected = new AdminDashboardStatsResponse(
                0L,
                BigDecimal.ZERO,
                0L,
                0L
        );
        AdminDashboardRepository fakeRepo = new AdminDashboardRepository(null) {
            @Override
            public AdminDashboardStatsResponse getStats() {
                return expected;
            }
        };

        AdminDashboardService service = new AdminDashboardService(fakeRepo);
        AdminDashboardStatsResponse actual = service.getStats();

        assertNotNull(actual);
        assertEquals(0L, actual.totalOrders());
        assertEquals(BigDecimal.ZERO, actual.totalRevenue());
        assertEquals(0L, actual.totalUsers());
        assertEquals(0L, actual.totalRestaurants());
    }
}
