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
import vn.hutech.fooddelivery.admin.dto.AdminMerchantResponse;
import vn.hutech.fooddelivery.admin.dto.ApprovalStatusRequest;
import vn.hutech.fooddelivery.common.PageResponse;

/**
 * Controller quản trị và duyệt chủ quán (Merchants) dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Merchants", description = "Quản lý và duyệt chủ quán dành cho Quản trị viên")
public class AdminMerchantController {

    private final AdminMerchantService adminMerchantService;

    public AdminMerchantController(AdminMerchantService adminMerchantService) {
        this.adminMerchantService = adminMerchantService;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách chủ quán kèm bộ lọc trạng thái và phân trang")
    public PageResponse<AdminMerchantResponse> getMerchants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminMerchantService.getMerchants(keyword, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một chủ quán theo ID")
    public AdminMerchantResponse getMerchantById(@PathVariable Long id) {
        return adminMerchantService.getMerchantById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Admin cập nhật trạng thái duyệt chủ quán (PENDING/APPROVED/REJECTED)")
    public AdminMerchantResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalStatusRequest request
    ) {
        return adminMerchantService.updateStatus(id, request.status());
    }
}

