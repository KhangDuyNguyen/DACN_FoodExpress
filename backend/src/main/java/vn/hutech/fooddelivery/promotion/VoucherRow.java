package vn.hutech.fooddelivery.promotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Record DTO mapping từ câu lệnh SQL thuần của bảng vouchers.
 * Tuân thủ quy ước nhóm trong docs/CONVENTIONS.md.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record VoucherRow(
        Long id,
        String code,
        String title,
        String description,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrder,
        BigDecimal maxDiscount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer usageLimit,
        Integer perUserLimit,
        Integer usedCount,
        Long restaurantId,
        Long createdBy,
        String status,
        LocalDateTime createdAt
) {
}
