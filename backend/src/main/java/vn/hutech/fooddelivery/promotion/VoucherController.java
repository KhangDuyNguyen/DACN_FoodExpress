package vn.hutech.fooddelivery.promotion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.promotion.dto.VoucherResponse;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateResponse;
import vn.hutech.fooddelivery.security.AuthUser;

import java.math.BigDecimal;
import java.util.List;

/**
 * API tra cứu và kiểm tra voucher cho giỏ hàng của khách hàng.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/vouchers")
@Tag(name = "Vouchers", description = "API tra cứu và xác thực voucher áp dụng cho giỏ hàng")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách voucher khả dụng theo đơn hàng và nhà hàng")
    public List<VoucherResponse> getAvailableVouchers(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(defaultValue = "0") BigDecimal orderAmount,
            @AuthenticationPrincipal AuthUser user
    ) {
        Long userId = user != null ? user.id() : null;
        return voucherService.getAvailableVouchers(restaurantId, orderAmount, userId);
    }

    @PostMapping("/validate")
    @Operation(summary = "Kiểm tra mã voucher và tính toán mức giảm giá chính xác")
    public VoucherValidateResponse validateVoucher(
            @Valid @RequestBody VoucherValidateRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        Long userId = user != null ? user.id() : null;
        return voucherService.validateVoucher(request, userId);
    }
}

