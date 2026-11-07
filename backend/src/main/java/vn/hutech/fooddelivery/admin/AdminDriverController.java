package vn.hutech.fooddelivery.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.admin.dto.AdminDriverResponse;
import vn.hutech.fooddelivery.admin.dto.ApprovalStatusRequest;
import vn.hutech.fooddelivery.common.PageResponse;

/**
 * Controller quản trị và duyệt tài xế (Drivers) dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/admin/drivers")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Drivers", description = "Quản lý và duyệt tài xế dành cho Quản trị viên")
public class AdminDriverController {

    private final AdminDriverService adminDriverService;

    public AdminDriverController(AdminDriverService adminDriverService) {
        this.adminDriverService = adminDriverService;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách tài xế kèm bộ lọc trạng thái và phân trang")
    public PageResponse<AdminDriverResponse> getDrivers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminDriverService.getDrivers(keyword, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một tài xế theo ID")
    public AdminDriverResponse getDriverById(@PathVariable Long id) {
        return adminDriverService.getDriverById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Admin cập nhật trạng thái duyệt tài xế (PENDING/APPROVED/REJECTED)")
    public AdminDriverResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalStatusRequest request
    ) {
        return adminDriverService.updateStatus(id, request.status());
    }
}

