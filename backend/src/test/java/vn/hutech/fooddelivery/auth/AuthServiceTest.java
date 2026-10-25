package vn.hutech.fooddelivery.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.hutech.fooddelivery.auth.dto.LoginRequest;
import vn.hutech.fooddelivery.auth.dto.RegisterRequest;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.UnauthorizedException;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;

    @InjectMocks
    AuthService authService;

    private static UserRow user(String status) {
        return new UserRow(1, "Nguyen Van A", null, "0911111111", "hash", "CUSTOMER", status,
                null, null, LocalDateTime.now());
    }

    @Test
    void registerWithExistingPhoneThrowsConflict() {
        when(userRepository.findByPhone("0911111111")).thenReturn(Optional.of(user("ACTIVE")));
        RegisterRequest req = new RegisterRequest("Nguyen Van B", "0911111111", null, "matkhau123", "CUSTOMER");

        assertThrows(ConflictException.class, () -> authService.register(req));
        verify(userRepository, never()).insert(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void loginWithWrongPasswordThrowsUnauthorizedWithGenericMessage() {
        when(userRepository.findByPhoneOrEmail("0911111111")).thenReturn(Optional.of(user("ACTIVE")));
        when(passwordEncoder.matches("sai_mat_khau", "hash")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("0911111111", "sai_mat_khau")));
        assertEquals("Sai tài khoản hoặc mật khẩu", ex.getMessage());
        verify(userRepository, never()).updateLastLogin(anyLong());
    }

    @Test
    void loginWithUnknownUserThrowsSameGenericMessage() {
        when(userRepository.findByPhoneOrEmail("0999999999")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("0999999999", "matkhau123")));
        assertEquals("Sai tài khoản hoặc mật khẩu", ex.getMessage());
    }

    @Test
    void loginWithLockedUserThrowsForbidden() {
        when(userRepository.findByPhoneOrEmail("0911111111")).thenReturn(Optional.of(user("LOCKED")));
        when(passwordEncoder.matches("matkhau123", "hash")).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> authService.login(new LoginRequest("0911111111", "matkhau123")));
        verify(userRepository, never()).updateLastLogin(anyLong());
    }
}
