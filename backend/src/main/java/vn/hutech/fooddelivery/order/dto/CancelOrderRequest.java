package vn.hutech.fooddelivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Khach huy don: bat buoc ghi ly do (luu vao orders.cancel_reason). */
public record CancelOrderRequest(
        @NotBlank(message = "Vui lòng nhập lý do hủy đơn")
        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String reason
) {
}
