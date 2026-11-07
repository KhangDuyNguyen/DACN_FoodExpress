package vn.hutech.fooddelivery.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.auth.dto.AuthResponse;
import vn.hutech.fooddelivery.auth.dto.LoginRequest;
import vn.hutech.fooddelivery.auth.dto.RegisterRequest;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.UnauthorizedException;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;
import vn.hutech.fooddelivery.user.dto.UserResponse;
// ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - REPOSITORY =====
import vn.hutech.fooddelivery.driver.DriverRepository;
// ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - REPOSITORY =====
// ===== BẮT ĐẦU BỔ SUNG - ĐIỆP: REGISTER MERCHANT - REPOSITORY =====
import vn.hutech.fooddelivery.admin.AdminMerchantRepository;
// ===== KẾT THÚC BỔ SUNG - ĐIỆP: REGISTER MERCHANT - REPOSITORY =====

/** Nghiep vu dang ky va dang nhap. */
@Service
public class AuthService {

    /** Dung chung cho moi truong hop sai, de khong lo tai khoan co ton tai hay khong. */
    static final String INVALID_CREDENTIALS = "Sai tài khoản hoặc mật khẩu";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - DEPENDENCY =====
    private final DriverRepository driverRepository;
    // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - DEPENDENCY =====
    // ===== BẮT ĐẦU BỔ SUNG - ĐIỆP: REGISTER MERCHANT - DEPENDENCY =====
    private final AdminMerchantRepository adminMerchantRepository;
    // ===== KẾT THÚC BỔ SUNG - ĐIỆP: REGISTER MERCHANT - DEPENDENCY =====

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - INJECTION =====
                       DriverRepository driverRepository,
                       // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - INJECTION =====
                       // ===== BẮT ĐẦU BỔ SUNG - ĐIỆP: REGISTER MERCHANT - INJECTION =====
                       AdminMerchantRepository adminMerchantRepository
                       // ===== KẾT THÚC BỔ SUNG - ĐIỆP: REGISTER MERCHANT - INJECTION =====
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - INJECTION =====
        this.driverRepository = driverRepository;
        // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - INJECTION =====
        // ===== BẮT ĐẦU BỔ SUNG - ĐIỆP: REGISTER MERCHANT - INJECTION =====
        this.adminMerchantRepository = adminMerchantRepository;
        // ===== KẾT THÚC BỔ SUNG - ĐIỆP: REGISTER MERCHANT - INJECTION =====
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String phone = req.phone().trim();
        String email = normalizeEmail(req.email());

        if (userRepository.findByPhone(phone).isPresent()) {
            throw new ConflictException("Số điện thoại đã được đăng ký");
        }
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email đã được đăng ký");
        }

        long id;
        try {
            id = userRepository.insert(req.fullName().trim(), phone, email,
                    passwordEncoder.encode(req.password()), req.role());
        } catch (DuplicateKeyException ex) {
            // Hai request dang ky cung luc vuot qua buoc kiem tra o tren, unique key trong DB chan lai
            throw new ConflictException("Số điện thoại hoặc email đã được đăng ký");
        }

        // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER =====
        if ("DRIVER".equals(req.role())) {
            // Dung cung transaction dang ky; INSERT driver loi se rollback ca users.
            driverRepository.insert(id, req.vehicleType(), req.licensePlate());
        }
        // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER =====

        // ===== BẮT ĐẦU BỔ SUNG - ĐIỆP: REGISTER MERCHANT =====
        if ("MERCHANT".equals(req.role())) {
            // Dung cung transaction dang ky; INSERT merchant o trang thai PENDING
            adminMerchantRepository.insert(id, req.fullName().trim(), phone);
        }
        // ===== KẾT THÚC BỔ SUNG - ĐIỆP: REGISTER MERCHANT =====

        UserRow user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String identifier = req.identifier().trim();
        if (identifier.contains("@")) {
            identifier = identifier.toLowerCase();
        }

        UserRow user = userRepository.findByPhoneOrEmail(identifier)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (!passwordMatches(req.password(), user.passwordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        // Chi bao "bi khoa" khi mat khau da dung, nen khong lo thong tin cho nguoi doan mat khau
        if (user.isBlocked()) {
            throw new ForbiddenException("Tài khoản đã bị khóa");
        }

        userRepository.updateLastLogin(user.id());
        return toAuthResponse(user);
    }

    /**
     * Hash trong DB co the khong phai BCrypt hop le (vi du du lieu mau "$2a$10$dummyhash").
     * Khi do coi nhu sai mat khau (401), khong de loi 500.
     */
    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private AuthResponse toAuthResponse(UserRow user) {
        String token = jwtService.generateToken(user.id(), user.role());
        return new AuthResponse(token, UserResponse.from(user));
    }

    /** Email rong -> null (cot email UNIQUE, nhieu chuoi rong se bi trung). Email viet thuong. */
    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
