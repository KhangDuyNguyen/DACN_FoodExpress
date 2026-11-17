package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body cho PUT /api/merchant/restaurants/{id}/hours - Cập nhật giờ mở cửa.
 */
public record UpdateHoursRequest(
        @NotNull @Valid List<HourEntry> hours
) {
    public record HourEntry(
            @NotNull Integer dayOfWeek,   // 1=Thứ 2 ... 7=Chủ nhật
            @NotNull String openTime,     // "08:00"
            @NotNull String closeTime     // "22:00"
    ) {}
}
