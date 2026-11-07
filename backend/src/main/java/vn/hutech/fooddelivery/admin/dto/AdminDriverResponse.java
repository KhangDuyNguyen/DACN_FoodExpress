package vn.hutech.fooddelivery.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin phản hồi chi tiết về tài xế dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record AdminDriverResponse(
        long id,
        long userId,
        String fullName,
        String phone,
        String email,
        String avatarUrl,
        String vehicleType,
        String licensePlate,
        String status,
        boolean isOnline,
        BigDecimal currentLat,
        BigDecimal currentLng,
        BigDecimal avgRating,
        LocalDateTime createdAt
) {
}

