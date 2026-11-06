package vn.hutech.fooddelivery.restaurant.dto;

/**
 * Một chi nhánh khác của cùng chủ quán (cùng merchant_id), hiện ở trang chi tiết quán.
 */
public record BranchDTO(
        long id,
        String name,
        String address,
        String openStatus
) {}
