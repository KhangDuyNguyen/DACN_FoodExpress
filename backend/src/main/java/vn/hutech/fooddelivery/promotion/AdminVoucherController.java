package vn.hutech.fooddelivery.promotion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.promotion.dto.AdminVoucherRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherResponse;
import vn.hutech.fooddelivery.promotion.dto.VoucherStatusRequest;
import vn.hutech.fooddelivery.security.AuthUser;

/**
 * Controller quản trị Voucher toàn hệ thống dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Vouchers", description = "Quản lý voucher toàn sàn dành cho Quản trị viên")
public class AdminVoucherController {

    private final VoucherService voucherService;

    public AdminVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách voucher toàn sàn với bộ lọc và phân trang")
    public PageResponse<VoucherResponse> getVouchers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return voucherService.getAdminVouchers(keyword, discountType, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một voucher theo ID")
    public VoucherResponse getVoucherById(@PathVariable Long id) {
        return voucherService.getAdminVoucherById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Admin tạo mới voucher toàn sàn")
    public VoucherResponse createVoucher(
            @Valid @RequestBody AdminVoucherRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return voucherService.createAdminVoucher(request, user.id());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Admin cập nhật thông tin voucher")
    public VoucherResponse updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody AdminVoucherRequest request
    ) {
        return voucherService.updateAdminVoucher(id, request);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Admin cập nhật trạng thái voucher (ACTIVE/PAUSED/EXPIRED)")
    public void updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody VoucherStatusRequest request
    ) {
        voucherService.updateAdminVoucherStatus(id, request.status());
    }
}

