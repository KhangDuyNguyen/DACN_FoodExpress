package vn.hutech.fooddelivery.user;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.user.dto.ChangePasswordRequest;
import vn.hutech.fooddelivery.user.dto.UpdateProfileRequest;
import vn.hutech.fooddelivery.user.dto.UserResponse;

/** Thong tin tai khoan cua chinh nguoi dang dang nhap: xem, sua ho ten/email, doi mat khau. */
@Service
public class UserService {

    private static final String EMAIL_TAKEN = "Email đã được đăng ký";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
    }

    /**
     * Sua ho ten, email. userId lay tu JWT (khong nhan id tu client) nen chi sua duoc chinh minh.
     * Email chuan hoa giong luc dang ky (cat khoang trang, chu thuong) de so trung khong phan biet hoa thuong.
     */
    @Transactional
    public UserResponse updateProfile(long userId, UpdateProfileRequest req) {
        String email = normalizeEmail(req.email());
        // Email dang thuoc tai khoan KHAC -> 409. Giu nguyen email cua chinh minh thi van luu duoc.
        if (email != null) {
            userRepository.findByEmail(email)
                    .filter(other -> other.id() != userId)
                    .ifPresent(other -> {
                        throw new ConflictException(EMAIL_TAKEN);
                    });
        }
        try {
            userRepository.updateProfile(userId, req.fullName(), email); // ho ten da cat khoang trang trong DTO
        } catch (DuplicateKeyException ex) {
            // 2 nguoi cung luc doi sang cung 1 email: ca 2 qua buoc kiem tra tren, cot UNIQUE trong DB chan lai
            throw new ConflictException(EMAIL_TAKEN);
        }
        return getById(userId);
    }

    /** Doi mat khau: so mat khau hien tai voi ban bam BCrypt trong DB, dung moi luu ban bam cua mat khau moi. */
    @Transactional
    public void changePassword(long userId, ChangePasswordRequest req) {
        UserRow user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        if (!passwordEncoder.matches(req.currentPassword(), user.passwordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(req.newPassword(), user.passwordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        userRepository.updatePasswordHash(userId, passwordEncoder.encode(req.newPassword()));
    }

    /** Giong AuthService: email rong thi coi nhu khong co; con lai doi chu thuong (khoang trang da cat trong DTO). */
    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
