package vn.hutech.fooddelivery.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderPreviewRequest;
import vn.hutech.fooddelivery.order.dto.PriceBreakdown;
import vn.hutech.fooddelivery.security.AuthUser;

/** Dat hang cua khach. SecurityConfig chi cho CUSTOMER goi /api/orders/**. */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Đặt hàng (chỉ CUSTOMER)")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Xem trước tạm tính, phí giao, tổng tiền của giỏ hiện tại đến điểm giao")
    public ResponseEntity<PriceBreakdown> preview(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody OrderPreviewRequest req) {
        return ResponseEntity.ok(orderService.preview(user.id(), req));
    }

    @PostMapping
    @Operation(summary = "Đặt hàng từ giỏ hiện tại (giá lấy từ database, chạy trong 1 transaction)")
    public ResponseEntity<OrderDetailResponse> createOrder(@AuthenticationPrincipal AuthUser user,
                                                           @Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(user.id(), req));
    }
}
