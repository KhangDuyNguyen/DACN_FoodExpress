package vn.hutech.fooddelivery.restaurant.dto;

/**
 * Request body cho PATCH /api/merchant/items/{id}/availability - Bật/tắt còn món.
 */
public record AvailabilityRequest(boolean isAvailable) {}
