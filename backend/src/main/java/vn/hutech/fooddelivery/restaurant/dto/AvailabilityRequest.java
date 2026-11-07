package vn.hutech.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body cho PATCH /api/merchant/items/{id}/availability - Bật/tắt còn món.
 * @JsonProperty bắt buộc vì Jackson serialize boolean isXxx thành "xxx" (bỏ prefix is).
 */
public record AvailabilityRequest(
        @JsonProperty("isAvailable") boolean isAvailable
) {}
