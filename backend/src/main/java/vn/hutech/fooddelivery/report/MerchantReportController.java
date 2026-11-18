package vn.hutech.fooddelivery.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.report.dto.RevenueReportResponse;
import vn.hutech.fooddelivery.report.dto.TopItemsReportResponse;
import vn.hutech.fooddelivery.security.AuthUser;

import java.time.LocalDate;

/**
 * Controller báo cáo thống kê dành cho chủ quán (MERCHANT).
 * Đường dẫn /api/merchant/** được phân quyền role MERCHANT trong SecurityConfig.
 */
@RestController
@RequestMapping("/api/merchant/reports")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant Reports", description = "Báo cáo doanh thu và top món bán chạy cho chủ quán")
public class MerchantReportController {

    private final MerchantReportService reportService;

    public MerchantReportController(MerchantReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/revenue")
    @Operation(summary = "Báo cáo doanh thu theo quán và khoảng thời gian",
            description = "Trả về doanh thu và số đơn theo ngày liên tục (điền 0 vào ngày không có đơn)")
    public ResponseEntity<RevenueReportResponse> getRevenueReport(
            @RequestParam long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(reportService.getRevenueReport(restaurantId, from, to, user));
    }

    @GetMapping("/top-items")
    @Operation(summary = "Báo cáo top món bán chạy theo quán và khoảng thời gian",
            description = "Danh sách món ăn bán chạy nhất sắp xếp theo số lượng bán giảm dần")
    public ResponseEntity<TopItemsReportResponse> getTopItemsReport(
            @RequestParam long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(reportService.getTopItemsReport(restaurantId, from, to, limit, user));
    }
}
