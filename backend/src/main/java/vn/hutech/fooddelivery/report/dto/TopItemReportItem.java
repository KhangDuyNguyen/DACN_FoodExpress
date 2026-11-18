package vn.hutech.fooddelivery.report.dto;

import java.math.BigDecimal;

public record TopItemReportItem(
        Long itemId,
        String itemName,
        long quantitySold,
        BigDecimal revenue
) {
}
