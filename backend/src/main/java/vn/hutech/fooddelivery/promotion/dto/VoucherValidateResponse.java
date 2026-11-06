package vn.hutech.fooddelivery.promotion.dto;

import vn.hutech.fooddelivery.promotion.DiscountType;

import java.math.BigDecimal;

/**
 * Kết quả thẩm định và tính mức giảm của voucher từ Backend.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record VoucherValidateResponse(
        boolean valid,
        String message,
        Long voucherId,
        String code,
        String title,
        DiscountType discountType,
        BigDecimal discountAmount,
        BigDecimal finalOrderAmount,
        BigDecimal finalShippingFee,
        BigDecimal minOrder,
        BigDecimal maxDiscount
) {
    public static VoucherValidateResponse invalid(String message) {
        return new VoucherValidateResponse(
                false,
                message,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null
        );
    }
}

