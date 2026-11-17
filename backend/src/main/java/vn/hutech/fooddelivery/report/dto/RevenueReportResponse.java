package vn.hutech.fooddelivery.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueReportResponse(
        long restaurantId,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate from,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate to,
        BigDecimal totalRevenue,
        long totalOrders,
        List<DailyRevenueItem> data
) {
}
