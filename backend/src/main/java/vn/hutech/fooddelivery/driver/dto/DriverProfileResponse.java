package vn.hutech.fooddelivery.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Thong tin ho so tai xe tra ra API, khong chua mat khau hay vi tri GPS. */
public record DriverProfileResponse(
        String fullName,
        String phone,
        String email,
        @Schema(allowableValues = {"MOTORBIKE", "BICYCLE", "CAR"})
        String vehicleType,
        String licensePlate,
        @Schema(description = "Trạng thái xét duyệt tài xế",
                allowableValues = {"PENDING", "APPROVED", "REJECTED", "SUSPENDED"})
        String status,
        boolean isOnline
) {
}
