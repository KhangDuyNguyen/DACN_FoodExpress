package vn.hutech.fooddelivery.promotion.dto;

import vn.hutech.fooddelivery.promotion.DiscountType;
import vn.hutech.fooddelivery.promotion.VoucherRow;
import vn.hutech.fooddelivery.promotion.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO trả về thông tin voucher cho client.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record VoucherResponse(
        Long id,
        String code,
        String title,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrder,
        BigDecimal maxDiscount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer usageLimit,
        Integer perUserLimit,
        Integer usedCount,
        Long restaurantId,
        String restaurantName,
        Long createdBy,
        VoucherStatus status,
        LocalDateTime createdAt
) {
    public static VoucherResponse fromRow(VoucherRow row, String restaurantName) {
        return new VoucherResponse(
                row.id(),
                row.code(),
                row.title(),
                row.description(),
                DiscountType.valueOf(row.discountType()),
                row.discountValue(),
                row.minOrder(),
                row.maxDiscount(),
                row.startAt(),
                row.endAt(),
                row.usageLimit(),
                row.perUserLimit(),
                row.usedCount(),
                row.restaurantId(),
                restaurantName,
                row.createdBy(),
                VoucherStatus.valueOf(row.status()),
                row.createdAt()
        );
    }
}

