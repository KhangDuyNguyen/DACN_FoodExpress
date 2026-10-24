package vn.hutech.fooddelivery.security;

/**
 * Nguoi dung dang goi API (lay tu JWT + database).
 * Trong controller: tham so {@code @AuthenticationPrincipal AuthUser user}; trong service: {@link CurrentUser#get()}.
 */
public record AuthUser(long id, String role) {
}
