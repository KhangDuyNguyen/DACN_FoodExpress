package vn.hutech.fooddelivery.auth.dto;

import vn.hutech.fooddelivery.user.dto.UserResponse;

/** Ket qua dang ky / dang nhap: JWT va thong tin user. */
public record AuthResponse(String token, UserResponse user) {
}
