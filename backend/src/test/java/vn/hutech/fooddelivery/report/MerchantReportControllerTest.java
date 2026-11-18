package vn.hutech.fooddelivery.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.GlobalExceptionHandler;
import vn.hutech.fooddelivery.report.dto.DailyRevenueItem;
import vn.hutech.fooddelivery.report.dto.RevenueReportResponse;
import vn.hutech.fooddelivery.report.dto.TopItemReportItem;
import vn.hutech.fooddelivery.report.dto.TopItemsReportResponse;
import vn.hutech.fooddelivery.security.AuthUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MerchantReportControllerTest {

    private MockMvc mockMvc;
    private final AuthUser merchantUser = new AuthUser(10L, "MERCHANT");

    @BeforeEach
    void setUp() {
        var auth = new UsernamePasswordAuthenticationToken(merchantUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MerchantReportService fakeService = new MerchantReportService(null) {
            @Override
            public RevenueReportResponse getRevenueReport(long restaurantId, LocalDate from, LocalDate to, AuthUser user) {
                if (restaurantId == 999L) {
                    throw new ForbiddenException("Bạn không có quyền truy cập báo cáo của quán này");
                }
                if (from.isAfter(to)) {
                    throw new BadRequestException("Ngày bắt đầu ('from') không được lớn hơn ngày kết thúc ('to')");
                }
                return new RevenueReportResponse(
                        restaurantId,
                        from,
                        to,
                        BigDecimal.valueOf(750000),
                        6L,
                        List.of(
                                new DailyRevenueItem(LocalDate.of(2026, 11, 1), BigDecimal.valueOf(250000), 2),
                                new DailyRevenueItem(LocalDate.of(2026, 11, 2), BigDecimal.ZERO, 0),
                                new DailyRevenueItem(LocalDate.of(2026, 11, 3), BigDecimal.valueOf(500000), 4)
                        )
                );
            }

            @Override
            public TopItemsReportResponse getTopItemsReport(long restaurantId, LocalDate from, LocalDate to, int limit, AuthUser user) {
                if (restaurantId == 999L) {
                    throw new ForbiddenException("Bạn không có quyền truy cập báo cáo của quán này");
                }
                if (from.isAfter(to)) {
                    throw new BadRequestException("Ngày bắt đầu ('from') không được lớn hơn ngày kết thúc ('to')");
                }
                return new TopItemsReportResponse(
                        List.of(
                                new TopItemReportItem(1L, "Trà sữa Oolong", 25, BigDecimal.valueOf(750000)),
                                new TopItemReportItem(2L, "Cơm tấm đặc biệt", 15, BigDecimal.valueOf(975000))
                        )
                );
            }
        };

        MerchantReportController controller = new MerchantReportController(fakeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/merchant/reports/revenue thành công -> 200 OK và đúng định dạng JSON")
    void getRevenueReport_success() throws Exception {
        mockMvc.perform(get("/api/merchant/reports/revenue")
                        .param("restaurantId", "100")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(100))
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-03"))
                .andExpect(jsonPath("$.totalRevenue").value(750000))
                .andExpect(jsonPath("$.totalOrders").value(6))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.data[0].revenue").value(250000))
                .andExpect(jsonPath("$.data[0].orderCount").value(2))
                .andExpect(jsonPath("$.data[1].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data[1].revenue").value(0))
                .andExpect(jsonPath("$.data[1].orderCount").value(0))
                .andExpect(jsonPath("$.data[2].date").value("2026-11-03"))
                .andExpect(jsonPath("$.data[2].revenue").value(500000))
                .andExpect(jsonPath("$.data[2].orderCount").value(4));
    }

    @Test
    @DisplayName("GET /api/merchant/reports/top-items thành công -> 200 OK và đúng định dạng JSON")
    void getTopItemsReport_success() throws Exception {
        mockMvc.perform(get("/api/merchant/reports/top-items")
                        .param("restaurantId", "100")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-07")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].itemId").value(1))
                .andExpect(jsonPath("$.data[0].itemName").value("Trà sữa Oolong"))
                .andExpect(jsonPath("$.data[0].quantitySold").value(25))
                .andExpect(jsonPath("$.data[0].revenue").value(750000))
                .andExpect(jsonPath("$.data[1].itemId").value(2))
                .andExpect(jsonPath("$.data[1].itemName").value("Cơm tấm đặc biệt"))
                .andExpect(jsonPath("$.data[1].quantitySold").value(15))
                .andExpect(jsonPath("$.data[1].revenue").value(975000));
    }

    @Test
    @DisplayName("Không phải chủ quán -> ForbiddenException (403 Forbidden)")
    void notOwner_returns403() throws Exception {
        mockMvc.perform(get("/api/merchant/reports/revenue")
                        .param("restaurantId", "999")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-03"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập báo cáo của quán này"));
    }

    @Test
    @DisplayName("from > to -> 400 Bad Request")
    void fromAfterTo_returns400() throws Exception {
        mockMvc.perform(get("/api/merchant/reports/revenue")
                        .param("restaurantId", "100")
                        .param("from", "2026-11-10")
                        .param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Ngày bắt đầu ('from') không được lớn hơn ngày kết thúc ('to')"));
    }

    @Test
    @DisplayName("Thiếu tham số bắt buộc -> 400 Bad Request")
    void missingParameter_returns400() throws Exception {
        mockMvc.perform(get("/api/merchant/reports/revenue")
                        .param("restaurantId", "100")
                        .param("from", "2026-11-01")) // thiếu 'to'
                .andExpect(status().isBadRequest());
    }
}
