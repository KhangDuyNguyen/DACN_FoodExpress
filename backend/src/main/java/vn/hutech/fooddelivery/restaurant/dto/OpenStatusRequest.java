package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body cho PATCH /api/merchant/restaurants/{id}/open-status - Mở/đóng quán.
 */
public record OpenStatusRequest(
        @NotNull @Pattern(regexp = "OPEN|CLOSED|TEMP_CLOSED") String openStatus
) {}
