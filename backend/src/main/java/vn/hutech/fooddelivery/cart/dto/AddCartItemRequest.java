package vn.hutech.fooddelivery.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Them mon vao gio. Client chi gui id mon, so luong va id tuy chon, KHONG gui gia (gia doc tu database).
 * replaceCart = true: dong y xoa gio cua quan khac de them mon cua quan nay.
 */
public record AddCartItemRequest(
        @NotNull(message = "Vui lòng chọn món")
        Long menuItemId,

        @NotNull(message = "Vui lòng nhập số lượng")
        @Min(value = 1, message = "Số lượng từ 1 đến 99")
        @Max(value = 99, message = "Số lượng từ 1 đến 99")
        Integer quantity,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note,

        // Khong gui thi mac dinh false
        boolean replaceCart,

        // id cac tuy chon (size, topping...) khach chon. Mon khong co tuy chon thi bo trong.
        @Size(max = 20, message = "Chọn tối đa 20 tùy chọn")
        List<@NotNull(message = "Tùy chọn không hợp lệ") Long> optionIds
) {

    /** Khong gui optionIds thi coi nhu khong chon tuy chon nao. */
    public AddCartItemRequest {
        optionIds = optionIds == null ? List.of() : optionIds;
    }
}
