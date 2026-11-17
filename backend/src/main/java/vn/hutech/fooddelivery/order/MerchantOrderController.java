package vn.hutech.fooddelivery.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;
import vn.hutech.fooddelivery.order.dto.MerchantOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.security.AuthUser;

/** Don cua cac quan thuoc chu quan dang dang nhap. /api/merchant/** chi MERCHANT goi duoc (SecurityConfig). */
@RestController
@RequestMapping("/api/merchant/orders")
@Tag(name = "Merchant - Đơn hàng", description = "Chủ quán xem đơn và đổi trạng thái đơn của quán mình")
public class MerchantOrderController {

    private final OrderManagementService orderManagementService;

    public MerchantOrderController(OrderManagementService orderManagementService) {
        this.orderManagementService = orderManagementService;
    }

    @GetMapping
    @Operation(summary = "Đơn của các quán mình sở hữu; restaurantId của chủ khác: 403; không tồn tại: 404")
    public ResponseEntity<PageResponse<MerchantOrderSummaryResponse>> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) @Pattern(regexp = OrderController.STATUS_PATTERN) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(orderManagementService.listMerchantOrders(user.id(), restaurantId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết 1 đơn của quán mình (đơn quán khác: 403)")
    public ResponseEntity<OrderDetailResponse> detail(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return ResponseEntity.ok(orderManagementService.getMerchantOrder(user.id(), id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái đơn theo đúng thứ tự: nhận đơn, bắt đầu giao, hoàn thành, hoặc hủy (kèm lý do)")
    public ResponseEntity<OrderDetailResponse> changeStatus(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable long id,
                                                            @Valid @RequestBody ChangeOrderStatusRequest req) {
        return ResponseEntity.ok(orderManagementService.changeStatusByMerchant(user.id(), id, req));
    }
}
