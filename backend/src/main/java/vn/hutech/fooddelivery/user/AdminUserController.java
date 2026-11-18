package vn.hutech.fooddelivery.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.user.dto.AdminUserResponse;
import vn.hutech.fooddelivery.user.dto.ChangeUserStatusRequest;

/** Admin quan ly nguoi dung. /api/admin/** chi ADMIN goi duoc (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin - Người dùng", description = "Xem, tìm, khóa / mở khóa tài khoản")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "Danh sách người dùng, lọc theo vai trò, trạng thái; q tìm theo họ tên, SĐT, email")
    public ResponseEntity<PageResponse<AdminUserResponse>> list(
            @RequestParam(required = false) @Pattern(regexp = "^$|CUSTOMER|MERCHANT|DRIVER|ADMIN") String role,
            @RequestParam(required = false) @Pattern(regexp = "^$|ACTIVE|LOCKED|PENDING|DELETED") String status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(adminUserService.list(role, status, q, page, size));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khóa (LOCKED) hoặc mở khóa (ACTIVE) tài khoản; không khóa được tài khoản ADMIN")
    public ResponseEntity<AdminUserResponse> changeStatus(@PathVariable long id,
                                                          @Valid @RequestBody ChangeUserStatusRequest req) {
        return ResponseEntity.ok(adminUserService.changeStatus(id, req.status()));
    }
}
