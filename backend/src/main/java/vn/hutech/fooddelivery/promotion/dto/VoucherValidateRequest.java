package vn.hutech.fooddelivery.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request xác thực voucher khi người dùng nhập mã trong giỏ hàng / đặt đơn.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record VoucherValidateRequest(
        @NotBlank(message = "Mã voucher không được để trống")
        String code,

        Long restaurantId,

        @NotNull(message = "Giá trị đơn hàng không được để trống")
        @DecimalMin(value = "0.0", inclusive = true, message = "Giá trị đơn hàng phải >= 0")
        BigDecimal orderAmount,

        BigDecimal shippingFee
) {
    public BigDecimal getEffectiveShippingFee() {
        return shippingFee != null && shippingFee.compareTo(BigDecimal.ZERO) >= 0
                ? shippingFee
                : BigDecimal.ZERO;
    }
}

