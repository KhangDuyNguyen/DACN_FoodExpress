package vn.hutech.fooddelivery.driver.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Don hien tai lay tu database; khong suy ra tu offer hoac thong bao Accept luu tren thiet bi. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DriverCurrentOrderResponse(
        @Schema(description = "null khi da xac nhan khong co don hoat dong",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        CurrentOrder currentOrder,
        @Schema(description = "Gio Backend, cung quy uoc LocalDateTime voi cac API Driver",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime serverTime
) {
    // OpenAPI 3.1 dung types chua "null"; nullable=true don le chi danh cho OpenAPI 3.0.
    @Schema(name = "DriverCurrentOrder", types = {"object", "null"}, requiredProperties = {
            "orderId", "orderCode", "status", "createdAt", "updatedAt", "restaurantId", "restaurantName",
            "restaurantAddress", "restaurantPhone", "pickupLat", "pickupLng", "receiverName", "receiverPhone",
            "deliveryAddress", "deliveryLat", "deliveryLng", "note"
    })
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CurrentOrder(
            long orderId,
            String orderCode,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long restaurantId,
            String restaurantName,
            String restaurantAddress,
            @Schema(nullable = true, types = {"string", "null"}) String restaurantPhone,
            BigDecimal pickupLat,
            BigDecimal pickupLng,
            String receiverName,
            String receiverPhone,
            String deliveryAddress,
            BigDecimal deliveryLat,
            BigDecimal deliveryLng,
            @Schema(nullable = true, types = {"string", "null"}) String note
    ) {
    }
}
