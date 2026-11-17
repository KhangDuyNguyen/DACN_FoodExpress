package vn.hutech.fooddelivery.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.report.dto.DailyRevenueItem;
import vn.hutech.fooddelivery.report.dto.RevenueReportResponse;
import vn.hutech.fooddelivery.report.dto.TopItemReportItem;
import vn.hutech.fooddelivery.report.dto.TopItemsReportResponse;
import vn.hutech.fooddelivery.security.AuthUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MerchantReportServiceTest {

    private final AuthUser merchantUser = new AuthUser(100L, "MERCHANT");
    private final AuthUser nonMerchantUser = new AuthUser(200L, "CUSTOMER");

    @Test
    @DisplayName("Báo cáo doanh thu: điền đủ các ngày trống với doanh thu 0 và số đơn 0")
    void getRevenueReport_fillsMissingDates() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 4);

        // Giả lập DB chỉ có đơn ở ngày 1 và ngày 3; ngày 2 và 4 không có đơn
        Map<LocalDate, MerchantReportRepository.DailyStat> mockStats = Map.of(
                LocalDate.of(2026, 11, 1),
                new MerchantReportRepository.DailyStat(LocalDate.of(2026, 11, 1), BigDecimal.valueOf(150000), 2),
                LocalDate.of(2026, 11, 3),
                new MerchantReportRepository.DailyStat(LocalDate.of(2026, 11, 3), BigDecimal.valueOf(300000), 3)
        );

        MerchantReportRepository fakeRepo = new MerchantReportRepository(null) {
            @Override
            public boolean isRestaurantOwner(long restaurantId, long userId) {
                return restaurantId == 10L && userId == 100L;
            }

            @Override
            public Map<LocalDate, DailyStat> getDailyRevenueStats(long restaurantId, LocalDate f, LocalDate t) {
                return mockStats;
            }
        };

        MerchantReportService service = new MerchantReportService(fakeRepo);
        RevenueReportResponse response = service.getRevenueReport(10L, from, to, merchantUser);

        assertNotNull(response);
        assertEquals(10L, response.restaurantId());
        assertEquals(from, response.from());
        assertEquals(to, response.to());
        assertEquals(BigDecimal.valueOf(450000), response.totalRevenue());
        assertEquals(5L, response.totalOrders());

        List<DailyRevenueItem> data = response.data();
        assertEquals(4, data.size(), "Phải có đủ 4 ngày liên tục từ 1/11 đến 4/11");

        // Ngày 1: có đơn
        assertEquals(LocalDate.of(2026, 11, 1), data.get(0).date());
        assertEquals(BigDecimal.valueOf(150000), data.get(0).revenue());
        assertEquals(2L, data.get(0).orderCount());

        // Ngày 2: trống, điền 0
        assertEquals(LocalDate.of(2026, 11, 2), data.get(1).date());
        assertEquals(BigDecimal.ZERO, data.get(1).revenue());
        assertEquals(0L, data.get(1).orderCount());

        // Ngày 3: có đơn
        assertEquals(LocalDate.of(2026, 11, 3), data.get(2).date());
        assertEquals(BigDecimal.valueOf(300000), data.get(2).revenue());
        assertEquals(3L, data.get(2).orderCount());

        // Ngày 4: trống, điền 0
        assertEquals(LocalDate.of(2026, 11, 4), data.get(3).date());
        assertEquals(BigDecimal.ZERO, data.get(3).revenue());
        assertEquals(0L, data.get(3).orderCount());
    }

    @Test
    @DisplayName("Báo cáo top món bán chạy: trả về đúng danh sách đã sắp xếp và giới hạn")
    void getTopItemsReport_success() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 7);

        List<TopItemReportItem> mockTopItems = List.of(
                new TopItemReportItem(1L, "Cơm tấm sườn nướng", 50, BigDecimal.valueOf(2250000)),
                new TopItemReportItem(2L, "Trà sữa trân châu", 35, BigDecimal.valueOf(875000)),
                new TopItemReportItem(3L, "Bún bò Huế", 20, BigDecimal.valueOf(900000))
        );

        MerchantReportRepository fakeRepo = new MerchantReportRepository(null) {
            @Override
            public boolean isRestaurantOwner(long restaurantId, long userId) {
                return restaurantId == 10L && userId == 100L;
            }

            @Override
            public List<TopItemReportItem> getTopSellingItems(long restaurantId, LocalDate f, LocalDate t, int limit) {
                assertEquals(10, limit);
                return mockTopItems;
            }
        };

        MerchantReportService service = new MerchantReportService(fakeRepo);
        TopItemsReportResponse response = service.getTopItemsReport(10L, from, to, 10, merchantUser);

        assertNotNull(response);
        assertEquals(3, response.data().size());
        assertEquals("Cơm tấm sườn nướng", response.data().get(0).itemName());
        assertEquals(50, response.data().get(0).quantitySold());
        assertEquals(BigDecimal.valueOf(2250000), response.data().get(0).revenue());
    }

    @Test
    @DisplayName("Ném ForbiddenException khi không phải chủ quán của restaurantId")
    void notOwner_throwsForbiddenException() {
        MerchantReportRepository fakeRepo = new MerchantReportRepository(null) {
            @Override
            public boolean isRestaurantOwner(long restaurantId, long userId) {
                return false; // Không phải chủ quán
            }
        };

        MerchantReportService service = new MerchantReportService(fakeRepo);
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 7);

        assertThrows(ForbiddenException.class, () ->
                service.getRevenueReport(10L, from, to, merchantUser));

        assertThrows(ForbiddenException.class, () ->
                service.getTopItemsReport(10L, from, to, 10, merchantUser));
    }

    @Test
    @DisplayName("Ném ForbiddenException khi user không có role MERCHANT")
    void nonMerchantRole_throwsForbiddenException() {
        MerchantReportRepository fakeRepo = new MerchantReportRepository(null);
        MerchantReportService service = new MerchantReportService(fakeRepo);

        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 7);

        assertThrows(ForbiddenException.class, () ->
                service.getRevenueReport(10L, from, to, nonMerchantUser));

        assertThrows(ForbiddenException.class, () ->
                service.getTopItemsReport(10L, from, to, 10, nonMerchantUser));
    }

    @Test
    @DisplayName("Ném BadRequestException khi from > to")
    void fromAfterTo_throwsBadRequestException() {
        MerchantReportRepository fakeRepo = new MerchantReportRepository(null) {
            @Override
            public boolean isRestaurantOwner(long restaurantId, long userId) {
                return true;
            }
        };
        MerchantReportService service = new MerchantReportService(fakeRepo);

        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 1);

        BadRequestException ex1 = assertThrows(BadRequestException.class, () ->
                service.getRevenueReport(10L, from, to, merchantUser));
        assertTrue(ex1.getMessage().contains("không được lớn hơn ngày kết thúc"));

        assertThrows(BadRequestException.class, () ->
                service.getTopItemsReport(10L, from, to, 10, merchantUser));
    }

    @Test
    @DisplayName("Ném BadRequestException khi from hoặc to bị null")
    void nullDates_throwsBadRequestException() {
        MerchantReportRepository fakeRepo = new MerchantReportRepository(null) {
            @Override
            public boolean isRestaurantOwner(long restaurantId, long userId) {
                return true;
            }
        };
        MerchantReportService service = new MerchantReportService(fakeRepo);

        assertThrows(BadRequestException.class, () ->
                service.getRevenueReport(10L, null, LocalDate.now(), merchantUser));

        assertThrows(BadRequestException.class, () ->
                service.getTopItemsReport(10L, LocalDate.now(), null, 10, merchantUser));
    }
}
