package vn.hutech.fooddelivery.assignment;

import java.time.LocalDateTime;

/** Lap lai thanh cong tra lai cung status/respondedAt, khong ghi them ban ghi. */
public record DriverOfferActionResponse(long offerId, long orderId, String status, LocalDateTime respondedAt) {
}
