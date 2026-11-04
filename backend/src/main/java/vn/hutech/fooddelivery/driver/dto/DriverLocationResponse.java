package vn.hutech.fooddelivery.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DriverLocationResponse(
        BigDecimal lat,
        BigDecimal lng,
        @Schema(description = "Thời gian Backend ghi nhận vị trí, chính xác đến giây")
        LocalDateTime recordedAt
) {
}
