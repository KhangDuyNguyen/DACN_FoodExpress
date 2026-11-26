package vn.hutech.fooddelivery.user.dto;

import vn.hutech.fooddelivery.user.UserRow;

import java.time.LocalDateTime;

/** 1 dong trong trang admin quan ly nguoi dung. Co y KHONG co passwordHash. */
public record AdminUserResponse(
        long id,
        String fullName,
        String phone,
        String email,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
    public static AdminUserResponse from(UserRow row) {
        return new AdminUserResponse(row.id(), row.fullName(), row.phone(), row.email(), row.role(), row.status(),
                row.createdAt(), row.lastLoginAt());
    }
}
