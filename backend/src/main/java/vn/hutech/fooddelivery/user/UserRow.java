package vn.hutech.fooddelivery.user;

import java.time.LocalDateTime;

/**
 * Mot dong trong bang users. Chi dung ben trong backend (co passwordHash),
 * KHONG tra truc tiep ra API, hay doi sang UserResponse.
 */
public record UserRow(
        long id,
        String fullName,
        String email,
        String phone,
        String passwordHash,
        String role,
        String status,
        String avatarUrl,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {

    /** Tai khoan bi khoa hoac da xoa thi khong duoc dang nhap / goi API. */
    public boolean isBlocked() {
        return "LOCKED".equals(status) || "DELETED".equals(status);
    }
}
