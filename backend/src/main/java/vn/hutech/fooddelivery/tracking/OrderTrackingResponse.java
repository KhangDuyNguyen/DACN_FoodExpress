package vn.hutech.fooddelivery.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderTrackingResponse(long orderId, String orderCode, String status,
                                    @JsonInclude(JsonInclude.Include.ALWAYS) Location latestLocation) {
    public record Location(BigDecimal lat, BigDecimal lng, LocalDateTime recordedAt) {
    }
}
