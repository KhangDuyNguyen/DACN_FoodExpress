package vn.hutech.fooddelivery.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dat hang tu gio hien tai. Client KHONG gui danh sach mon hay gia:
 * server lay mon tu gio trong database va tu tinh tien.
 */
public record CreateOrderRequest(
        @NotBlank(message = "Vui lòng nhập tên người nhận")
        @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
        String receiverName,

        @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
        @Pattern(regexp = "0\\d{9}", message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0")
        String receiverPhone,

        @NotBlank(message = "Vui lòng nhập địa chỉ giao hàng")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String deliveryAddress,

        @NotNull(message = "Thiếu vĩ độ điểm giao")
        @DecimalMin(value = "-90", message = "Vĩ độ không hợp lệ")
        @DecimalMax(value = "90", message = "Vĩ độ không hợp lệ")
        Double deliveryLat,

        @NotNull(message = "Thiếu kinh độ điểm giao")
        @DecimalMin(value = "-180", message = "Kinh độ không hợp lệ")
        @DecimalMax(value = "180", message = "Kinh độ không hợp lệ")
        Double deliveryLng,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note,

        // TODO(khang): VNPAY, MOMO lam o phan thanh toan (tuan sau)
        @NotBlank(message = "Vui lòng chọn cách thanh toán")
        @Pattern(regexp = "COD", message = "Hiện chỉ hỗ trợ thanh toán khi nhận hàng (COD)")
        String paymentMethod
) {
}
