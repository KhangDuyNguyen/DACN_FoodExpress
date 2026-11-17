package vn.hutech.fooddelivery.report.dto;

import java.util.List;

public record TopItemsReportResponse(
        List<TopItemReportItem> data
) {
}
