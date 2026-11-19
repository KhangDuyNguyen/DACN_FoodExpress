package vn.hutech.fooddelivery.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderTrackingResponse(long orderId, String orderCode, String status,
                                    @JsonInclude(JsonInclude.Include.ALWAYS) Location latestLocation,
                                    DeliveryPoint deliveryPoint) {
    public record DeliveryPoint(BigDecimal lat, BigDecimal lng) {
    }

    public record Location(BigDecimal lat, BigDecimal lng, LocalDateTime recordedAt) {
    }
}
