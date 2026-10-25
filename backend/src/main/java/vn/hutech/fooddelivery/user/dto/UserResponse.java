package vn.hutech.fooddelivery.user.dto;

import vn.hutech.fooddelivery.user.UserRow;

import java.time.LocalDateTime;

/** Thong tin user tra ra API. Co y KHONG co passwordHash. */
public record UserResponse(
        long id,
        String fullName,
        String phone,
        String email,
        String role,
        String status,
        String avatarUrl,
        LocalDateTime createdAt
) {

    public static UserResponse from(UserRow row) {
        return new UserResponse(row.id(), row.fullName(), row.phone(), row.email(),
                row.role(), row.status(), row.avatarUrl(), row.createdAt());
    }
}
