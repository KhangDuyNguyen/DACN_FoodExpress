package vn.hutech.fooddelivery.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.hutech.fooddelivery.promotion.DiscountType;
import vn.hutech.fooddelivery.promotion.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO tạo mới hoặc cập nhật voucher từ Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record AdminVoucherRequest(
        @NotBlank(message = "Mã voucher không được để trống")
        @Size(max = 30, message = "Mã voucher không vượt quá 30 ký tự")
        String code,

        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 150, message = "Tiêu đề không vượt quá 150 ký tự")
        String title,

        @Size(max = 500, message = "Mô tả không vượt quá 500 ký tự")
        String description,

        @NotNull(message = "Loại giảm giá không được để trống")
        DiscountType discountType,

        @NotNull(message = "Giá trị giảm giá không được để trống")
        @DecimalMin(value = "0.0", inclusive = true, message = "Giá trị giảm giá phải >= 0")
        BigDecimal discountValue,

        @NotNull(message = "Đơn hàng tối thiểu không được để trống")
        @DecimalMin(value = "0.0", inclusive = true, message = "Đơn hàng tối thiểu phải >= 0")
        BigDecimal minOrder,

        BigDecimal maxDiscount,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        LocalDateTime startAt,

        @NotNull(message = "Thời gian kết thúc không được để trống")
        LocalDateTime endAt,

        @Min(value = 1, message = "Giới hạn sử dụng phải >= 1")
        Integer usageLimit,

        @Min(value = 1, message = "Giới hạn mỗi user phải >= 1")
        Integer perUserLimit,

        Long restaurantId,

        VoucherStatus status
) {
}

