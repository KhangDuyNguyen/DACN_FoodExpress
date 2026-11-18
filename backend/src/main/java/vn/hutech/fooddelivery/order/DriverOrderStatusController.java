package vn.hutech.fooddelivery.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.order.dto.DeliveryStatusRequest;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.security.AuthUser;

/**
 * Tai xe cap nhat buoc giao cho don minh da nhan. /api/driver/** chi DRIVER goi duoc (SecurityConfig).
 * Cung duong dan goc voi DriverOrderController cua An (GET /api/driver/orders/current), khac method/duong dan con.
 */
@RestController
@RequestMapping("/api/driver/orders")
@Tag(name = "Driver - Cập nhật giao hàng", description = "Tài xế báo đã lấy món, đã giao cho đơn mình đã nhận")
public class DriverOrderStatusController {

    private final OrderManagementService orderManagementService;

    public DriverOrderStatusController(OrderManagementService orderManagementService) {
        this.orderManagementService = orderManagementService;
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Tài xế đổi trạng thái đơn mình đã nhận: DELIVERING (đã lấy món) hoặc DELIVERED (đã giao)")
    public ResponseEntity<OrderDetailResponse> changeStatus(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable long id,
                                                            @Valid @RequestBody DeliveryStatusRequest req) {
        return ResponseEntity.ok(orderManagementService.changeStatusByDriver(user.id(), id, req.status()));
    }
}
