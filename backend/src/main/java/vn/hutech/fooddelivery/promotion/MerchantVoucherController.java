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
import vn.hutech.fooddelivery.promotion.dto.MerchantRestaurantOption;
import vn.hutech.fooddelivery.promotion.dto.MerchantVoucherRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherResponse;
import vn.hutech.fooddelivery.promotion.dto.VoucherStatusRequest;
import vn.hutech.fooddelivery.security.AuthUser;

import java.util.List;

/**
 * Controller quản lý voucher khuyến mãi dành cho Chủ cửa hàng (Merchant).
 * Kiểm soát quyền sở hữu chặt chẽ: chỉ quản lý voucher thuộc các quán ăn của chính mình.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/merchant/vouchers")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant Vouchers", description = "Quản lý voucher khuyến mãi của cửa hàng")
public class MerchantVoucherController {

    private final VoucherService voucherService;

    public MerchantVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách voucher thuộc các quán ăn của chủ cửa hàng")
    public PageResponse<VoucherResponse> getVouchers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthUser user
    ) {
        return voucherService.getMerchantVouchers(user.id(), keyword, status, page, size);
    }

    @GetMapping("/restaurants")
    @Operation(summary = "Lấy danh sách các nhà hàng của chủ quán để chọn khi tạo voucher")
    public List<MerchantRestaurantOption> getMyRestaurants(
            @AuthenticationPrincipal AuthUser user
    ) {
        return voucherService.getMerchantRestaurants(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Chủ quán tạo mới voucher riêng cho nhà hàng của mình")
    public VoucherResponse createVoucher(
            @Valid @RequestBody MerchantVoucherRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return voucherService.createMerchantVoucher(request, user.id());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Chủ quán cập nhật thông tin voucher của mình")
    public VoucherResponse updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody MerchantVoucherRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return voucherService.updateMerchantVoucher(id, request, user.id());
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Chủ quán cập nhật trạng thái voucher của mình")
    public void updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody VoucherStatusRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        voucherService.updateMerchantVoucherStatus(id, request.status(), user.id());
    }
}

