package vn.hutech.fooddelivery.admin.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.admin.dashboard.dto.AdminDashboardStatsResponse;

/**
 * Service xử lý logic thống kê tổng quan hệ thống cho Admin Dashboard.
 */
@Service
public class AdminDashboardService {

    private final AdminDashboardRepository adminDashboardRepository;

    public AdminDashboardService(AdminDashboardRepository adminDashboardRepository) {
        this.adminDashboardRepository = adminDashboardRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getStats() {
        return adminDashboardRepository.getStats();
    }
}
