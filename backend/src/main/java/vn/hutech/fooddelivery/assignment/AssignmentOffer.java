package vn.hutech.fooddelivery.assignment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Offer OFFERED vua duoc commit; distanceKm la gia tri DECIMAL(6,2) da luu. */
public record AssignmentOffer(long id, long orderId, long driverId, BigDecimal distanceKm,
                              LocalDateTime offeredAt, LocalDateTime expiresAt) {
}
