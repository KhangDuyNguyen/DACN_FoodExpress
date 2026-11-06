package vn.hutech.fooddelivery.restaurant.dto;

import java.math.BigDecimal;

/**
 * Chi tiết một món, kèm quán và nhóm món chứa nó.
 * Giá là VND kiểu BigDecimal (cột DECIMAL(12,0)), không dùng double cho tiền.
 */
public record MenuItemDetailDTO(
        long id,
        long restaurantId,
        long categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean isAvailable
) {}
