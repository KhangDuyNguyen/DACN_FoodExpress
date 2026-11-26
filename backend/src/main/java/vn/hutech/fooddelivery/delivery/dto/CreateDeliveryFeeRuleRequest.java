package vn.hutech.fooddelivery.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Admin tao quy tac phi giao moi. Tien la VND so nguyen (cot DECIMAL(12,0)). */
public record CreateDeliveryFeeRuleRequest(
        @NotBlank(message = "Vui lòng nhập tên quy tắc")
        @Size(max = 100, message = "Tên quy tắc tối đa 100 ký tự")
        String name,

        @NotNull(message = "Vui lòng nhập phí cơ bản")
        @DecimalMin(value = "0", message = "Phí cơ bản không được âm")
        @Digits(integer = 12, fraction = 0, message = "Phí cơ bản phải là số nguyên (VND)")
        BigDecimal baseFee,

        // Cot DECIMAL(5,2); gioi han 0-50 km cho hop ly (don xa nhat 30 km)
        @NotNull(message = "Vui lòng nhập số km cơ bản")
        @DecimalMin(value = "0", message = "Số km cơ bản không được âm")
        @DecimalMax(value = "50", message = "Số km cơ bản tối đa 50")
        @Digits(integer = 2, fraction = 2, message = "Số km cơ bản tối đa 2 chữ số thập phân")
        BigDecimal baseDistanceKm,

        @NotNull(message = "Vui lòng nhập phí mỗi km")
        @DecimalMin(value = "0", message = "Phí mỗi km không được âm")
        @Digits(integer = 12, fraction = 0, message = "Phí mỗi km phải là số nguyên (VND)")
        BigDecimal perKmFee
) {
}
