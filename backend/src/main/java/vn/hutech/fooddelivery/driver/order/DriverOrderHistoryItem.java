package vn.hutech.fooddelivery.driver.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DriverOrderHistoryItem(long orderId, String orderCode, long restaurantId, String restaurantName,
                                      String status, LocalDateTime deliveredAt) {
}
