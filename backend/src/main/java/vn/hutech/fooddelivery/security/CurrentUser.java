package vn.hutech.fooddelivery.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.hutech.fooddelivery.common.UnauthorizedException;

/** Lay nguoi dung hien tai (do JwtAuthFilter dat vao SecurityContext). */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Tra ve nguoi dung dang dang nhap, chua dang nhap thi nem UnauthorizedException (401). */
    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new UnauthorizedException("Bạn chưa đăng nhập");
    }
}
