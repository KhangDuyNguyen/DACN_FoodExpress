package vn.hutech.fooddelivery.driver.order;

import java.time.LocalDateTime;

public record DriverOrderStatusResponse(long orderId, String status,
                                       LocalDateTime updatedAt, LocalDateTime deliveredAt) {
}
