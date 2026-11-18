package vn.hutech.fooddelivery.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenueItem(
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,
        BigDecimal revenue,
        long orderCount
) {
}
