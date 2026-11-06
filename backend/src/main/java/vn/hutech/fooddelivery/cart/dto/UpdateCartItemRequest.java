package vn.hutech.fooddelivery.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Doi so luong va ghi chu cua 1 dong trong gio (gui ca 2 truong, note de trong = xoa ghi chu). */
public record UpdateCartItemRequest(
        @NotNull(message = "Vui lòng nhập số lượng")
        @Min(value = 1, message = "Số lượng từ 1 đến 99")
        @Max(value = 99, message = "Số lượng từ 1 đến 99")
        Integer quantity,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
