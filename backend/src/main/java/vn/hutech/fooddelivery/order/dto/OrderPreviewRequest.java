package vn.hutech.fooddelivery.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Xem truoc tien cua gio hien tai: toa do diem giao de tinh phi ship,
 * voucherCode (khong bat buoc, tuan 5) de xem truoc so tien duoc giam.
 */
public record OrderPreviewRequest(
        @NotNull(message = "Thiếu vĩ độ điểm giao")
        @DecimalMin(value = "-90", message = "Vĩ độ không hợp lệ")
        @DecimalMax(value = "90", message = "Vĩ độ không hợp lệ")
        Double deliveryLat,

        @NotNull(message = "Thiếu kinh độ điểm giao")
        @DecimalMin(value = "-180", message = "Kinh độ không hợp lệ")
        @DecimalMax(value = "180", message = "Kinh độ không hợp lệ")
        Double deliveryLng,

        @Size(max = 30, message = "Mã voucher tối đa 30 ký tự")
        String voucherCode
) {
}
