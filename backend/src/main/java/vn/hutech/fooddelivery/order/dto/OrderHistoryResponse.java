package vn.hutech.fooddelivery.order.dto;

import java.time.LocalDateTime;

/** 1 lan doi trang thai don (bang order_status_history). */
public record OrderHistoryResponse(
        String status,
        LocalDateTime changedAt,
        String note
) {
}
