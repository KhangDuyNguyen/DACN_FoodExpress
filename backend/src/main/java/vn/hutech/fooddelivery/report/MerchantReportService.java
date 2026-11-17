package vn.hutech.fooddelivery.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.report.dto.DailyRevenueItem;
import vn.hutech.fooddelivery.report.dto.RevenueReportResponse;
import vn.hutech.fooddelivery.report.dto.TopItemReportItem;
import vn.hutech.fooddelivery.report.dto.TopItemsReportResponse;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.security.CurrentUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý nghiệp vụ thống kê báo cáo cho chủ quán (MERCHANT).
 */
@Service
public class MerchantReportService {

    private final MerchantReportRepository reportRepository;

    public MerchantReportService(MerchantReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Xác thực người dùng có vai trò MERCHANT và sở hữu nhà hàng restaurantId.
     */
    public void validateMerchantAndOwnership(long restaurantId, AuthUser user) {
        if (user == null) {
            user = CurrentUser.get();
        }
        if (!"MERCHANT".equalsIgnoreCase(user.role())) {
            throw new ForbiddenException("Chỉ chủ quán (MERCHANT) mới có quyền xem báo cáo");
        }
        if (!reportRepository.isRestaurantOwner(restaurantId, user.id())) {
            throw new ForbiddenException("Bạn không có quyền truy cập báo cáo của quán này");
        }
    }

    /**
     * Kiểm tra khoảng thời gian hợp lệ: from <= to.
     */
    public void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Ngày bắt đầu ('from') và ngày kết thúc ('to') là bắt buộc");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("Ngày bắt đầu ('from': " + from + ") không được lớn hơn ngày kết thúc ('to': " + to + ")");
        }
    }

    /**
     * Báo cáo doanh thu theo quán và khoảng thời gian [from, to].
     * Điền đầy đủ các ngày trống với doanh thu 0 và số đơn 0 để Recharts hiển thị liên tục.
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse getRevenueReport(long restaurantId, LocalDate from, LocalDate to, AuthUser user) {
        validateMerchantAndOwnership(restaurantId, user);
        validateDateRange(from, to);

        Map<LocalDate, MerchantReportRepository.DailyStat> statsMap =
                reportRepository.getDailyRevenueStats(restaurantId, from, to);

        List<DailyRevenueItem> continuousData = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalOrders = 0;

        LocalDate curr = from;
        while (!curr.isAfter(to)) {
            MerchantReportRepository.DailyStat stat = statsMap.get(curr);
            BigDecimal revenue = (stat != null && stat.revenue() != null) ? stat.revenue() : BigDecimal.ZERO;
            long orderCount = (stat != null) ? stat.orderCount() : 0L;

            continuousData.add(new DailyRevenueItem(curr, revenue, orderCount));
            totalRevenue = totalRevenue.add(revenue);
            totalOrders += orderCount;

            curr = curr.plusDays(1);
        }

        return new RevenueReportResponse(
                restaurantId,
                from,
                to,
                totalRevenue,
                totalOrders,
                continuousData
        );
    }

    /**
     * Báo cáo top món ăn bán chạy nhất theo số lượng.
     */
    @Transactional(readOnly = true)
    public TopItemsReportResponse getTopItemsReport(long restaurantId, LocalDate from, LocalDate to, int limit, AuthUser user) {
        validateMerchantAndOwnership(restaurantId, user);
        validateDateRange(from, to);

        if (limit <= 0) {
            limit = 10;
        }

        List<TopItemReportItem> items = reportRepository.getTopSellingItems(restaurantId, from, to, limit);
        return new TopItemsReportResponse(items);
    }
}
