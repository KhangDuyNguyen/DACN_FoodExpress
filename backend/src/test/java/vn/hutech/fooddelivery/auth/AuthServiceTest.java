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
// ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - TEST DEPENDENCIES =====
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import vn.hutech.fooddelivery.driver.DriverRepository;
// ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - TEST DEPENDENCIES =====

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
// ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - TEST HELPERS =====
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
// ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - TEST HELPERS =====

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;
    // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - MOCK REPOSITORY =====
    @Mock
    DriverRepository driverRepository;
    // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - MOCK REPOSITORY =====

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

    // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - TEST NGHIỆP VỤ =====
    @ParameterizedTest
    @ValueSource(strings = {"MOTORBIKE", "BICYCLE", "CAR"})
    void driverRegistrationCreatesProfileAfterUserAndBeforeJwt(String vehicleType) {
        String plate = "BICYCLE".equals(vehicleType) ? null : "59A1-12345";
        when(passwordEncoder.encode("matkhau123")).thenReturn("encoded-hash");
        when(userRepository.insert("Nguyen Van An", "0911111111", null, "encoded-hash", "DRIVER")).thenReturn(7L);
        when(userRepository.findById(7)).thenReturn(Optional.of(new UserRow(7, "Nguyen Van An", null,
                "0911111111", "encoded-hash", "DRIVER", "ACTIVE", null, null, LocalDateTime.now())));
        when(jwtService.generateToken(7, "DRIVER")).thenReturn("test-jwt");

        var response = authService.register(new RegisterRequest("Nguyen Van An", "0911111111", null,
                "matkhau123", "DRIVER", vehicleType, plate));

        assertEquals("test-jwt", response.token());
        assertEquals("DRIVER", response.user().role());
        var order = inOrder(userRepository, driverRepository, jwtService);
        order.verify(userRepository).insert("Nguyen Van An", "0911111111", null, "encoded-hash", "DRIVER");
        order.verify(driverRepository).insert(7, vehicleType, plate);
        order.verify(userRepository).findById(7);
        order.verify(jwtService).generateToken(7, "DRIVER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT"})
    void existingRolesRegisterWithoutVehicleOrDriverInsert(String role) {
        when(passwordEncoder.encode("matkhau123")).thenReturn("encoded-hash");
        when(userRepository.insert("Nguyen Van A", "0911111111", null, "encoded-hash", role)).thenReturn(7L);
        when(userRepository.findById(7)).thenReturn(Optional.of(new UserRow(7, "Nguyen Van A", null,
                "0911111111", "encoded-hash", role, "ACTIVE", null, null, LocalDateTime.now())));

        assertEquals(role, authService.register(new RegisterRequest("Nguyen Van A", "0911111111", null,
                "matkhau123", role)).user().role());
        verifyNoInteractions(driverRepository);
    }

    @Test
    void existingEmailStillReturnsConflictBeforeAnyInsert() {
        when(userRepository.findByEmail("an@example.com")).thenReturn(Optional.of(user("ACTIVE")));

        assertThrows(ConflictException.class, () -> authService.register(new RegisterRequest("Nguyen Van An",
                "0911111111", "AN@example.com", "matkhau123", "DRIVER", "BICYCLE", null)));
        verify(userRepository, never()).insert(anyString(), anyString(), nullable(String.class), anyString(), anyString());
        verifyNoInteractions(driverRepository, passwordEncoder, jwtService);
    }

    @Test
    void driverInsertFailurePropagatesWithoutIssuingJwt() {
        when(passwordEncoder.encode("matkhau123")).thenReturn("encoded-hash");
        when(userRepository.insert("Nguyen Van An", "0911111111", null, "encoded-hash", "DRIVER")).thenReturn(7L);
        doThrow(new DataIntegrityViolationException("driver insert failed"))
                .when(driverRepository).insert(eq(7L), eq("BICYCLE"), nullable(String.class));

        assertThrows(DataIntegrityViolationException.class, () -> authService.register(new RegisterRequest(
                "Nguyen Van An", "0911111111", null, "matkhau123", "DRIVER", "BICYCLE", null)));
        verifyNoInteractions(jwtService);
        verify(userRepository, never()).findById(anyLong());
    }
    // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - TEST NGHIỆP VỤ =====
}
