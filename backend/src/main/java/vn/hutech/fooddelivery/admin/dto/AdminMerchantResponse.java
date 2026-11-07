package vn.hutech.fooddelivery.admin.dto;

import java.time.LocalDateTime;

/**
 * Thông tin phản hồi chi tiết về chủ quán dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record AdminMerchantResponse(
        long id,
        long ownerUserId,
        String ownerFullName,
        String ownerEmail,
        String ownerPhone,
        String businessName,
        String taxCode,
        String contactPhone,
        String status,
        int totalRestaurants,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

