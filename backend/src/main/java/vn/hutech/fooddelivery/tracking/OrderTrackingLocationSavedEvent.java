package vn.hutech.fooddelivery.tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderTrackingLocationSavedEvent(long orderId, BigDecimal lat, BigDecimal lng, LocalDateTime recordedAt) {
}
