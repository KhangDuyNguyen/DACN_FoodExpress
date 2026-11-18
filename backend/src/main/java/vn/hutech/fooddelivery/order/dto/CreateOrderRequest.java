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

        // Khong bat buoc. Ma khong dung duoc thi 409 kem ly do, khong tao don (tuan 5)
        @Size(max = 30, message = "Mã voucher tối đa 30 ký tự")
        String voucherCode,

        // COD: tra khi nhan hang. VNPAY: tra truoc qua VNPay (tuan 5).
        // TODO(khang): MOMO lam sau VNPay (tuan 6), dung chung bang payments
        @NotBlank(message = "Vui lòng chọn cách thanh toán")
        @Pattern(regexp = "COD|VNPAY", message = "Chỉ hỗ trợ thanh toán khi nhận hàng (COD) hoặc VNPay")
        String paymentMethod
) {
}
