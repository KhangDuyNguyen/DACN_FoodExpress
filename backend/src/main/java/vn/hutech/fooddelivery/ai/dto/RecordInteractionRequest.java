package vn.hutech.fooddelivery.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request ghi nhận hành vi tương tác món ăn (POST /api/interactions).
 * Không nhận userId từ client: backend luôn lấy từ JWT.
 */
public record RecordInteractionRequest(
        @NotNull(message = "menuItemId không được để trống")
        @Positive(message = "menuItemId phải là số dương")
        Long menuItemId,

        @NotBlank(message = "type không được để trống")
        String type,

        @Min(value = 1, message = "quantity phải >= 1")
        @Max(value = 999, message = "quantity tối đa 999")
        Integer quantity,

        @Positive(message = "orderId phải là số dương")
        Long orderId,

        @Size(max = 64, message = "eventId tối đa 64 ký tự")
        String eventId
) {
}

