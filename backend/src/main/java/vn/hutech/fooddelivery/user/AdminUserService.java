package vn.hutech.fooddelivery.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.user.dto.AdminUserResponse;

/**
 * Admin quan ly nguoi dung: xem danh sach (loc, tim) va khoa / mo khoa tai khoan.
 * Chi ADMIN goi duoc (SecurityConfig chan /api/admin/** theo vai tro).
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(String role, String status, String keyword, int page, int size) {
        String cleanKeyword = blankToNull(keyword);
        long offset = (long) page * size;
        return PageResponse.of(
                userRepository.findForAdmin(blankToNull(role), blankToNull(status), cleanKeyword, size, offset)
                        .stream().map(AdminUserResponse::from).toList(),
                page, size,
                userRepository.countForAdmin(blankToNull(role), blankToNull(status), cleanKeyword));
    }

    /**
     * Khoa (LOCKED) hoac mo khoa (ACTIVE). Khoa co hieu luc ngay: dang nhap bi tu choi (403) va
     * JwtAuthFilter doc lai trang thai o moi request nen token dang dung cung het tac dung (401).
     */
    @Transactional
    public AdminUserResponse changeStatus(long userId, String newStatus) {
        UserRow user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        // Khong khoa tai khoan quan tri, tranh tu khoa minh hoac khoa admin khac
        if ("ADMIN".equals(user.role())) {
            throw new ConflictException("Không khóa được tài khoản quản trị");
        }
        if (!"ACTIVE".equals(user.status()) && !"LOCKED".equals(user.status())) {
            throw new ConflictException("Chỉ khóa hoặc mở khóa được tài khoản đang hoạt động hoặc đang bị khóa");
        }
        if (!newStatus.equals(user.status())) {
            userRepository.updateStatus(userId, newStatus);
        }
        return userRepository.findById(userId).map(AdminUserResponse::from).orElseThrow();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
