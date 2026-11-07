package vn.hutech.fooddelivery.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin phản hồi chi tiết về nhà hàng dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record AdminRestaurantResponse(
        long id,
        long merchantId,
        String merchantBusinessName,
        long merchantOwnerUserId,
        String merchantOwnerName,
        String merchantOwnerPhone,
        String merchantStatus,
        String name,
        String description,
        String phone,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String logoUrl,
        String coverUrl,
        String openStatus,
        String status,
        BigDecimal minOrderAmount,
        int prepTimeMinutes,
        BigDecimal avgRating,
        int ratingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

