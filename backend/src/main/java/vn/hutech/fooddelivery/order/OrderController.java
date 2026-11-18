package vn.hutech.fooddelivery.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.order.dto.CancelOrderRequest;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.dto.CreateOrderResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderPreviewRequest;
import vn.hutech.fooddelivery.order.dto.OrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.PriceBreakdown;
import vn.hutech.fooddelivery.security.AuthUser;

/** Dat hang va don cua khach. SecurityConfig chi cho CUSTOMER goi /api/orders/**. */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Đặt hàng và đơn của khách (chỉ CUSTOMER)")
public class OrderController {

    /** Cac trang thai dang dung (tuan 4 them READY_FOR_PICKUP), kiem tra tham so ?status= (cho phep rong de lay tat ca) */
    static final String STATUS_PATTERN = "^$|PLACED|PREPARING|READY_FOR_PICKUP|DELIVERING|DELIVERED|CANCELLED";
    /** Khach con loc duoc don VNPay chua tra tien (quan thi khong thay nhung don nay nen dung STATUS_PATTERN). */
    static final String CUSTOMER_STATUS_PATTERN = STATUS_PATTERN + "|PENDING_PAYMENT";

    private final OrderService orderService;
    private final OrderManagementService orderManagementService;

    public OrderController(OrderService orderService, OrderManagementService orderManagementService) {
        this.orderService = orderService;
        this.orderManagementService = orderManagementService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Xem trước tạm tính, phí giao, tổng tiền của giỏ hiện tại đến điểm giao")
    public ResponseEntity<PriceBreakdown> preview(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody OrderPreviewRequest req) {
        return ResponseEntity.ok(orderService.preview(user.id(), req));
    }

    @PostMapping
    @Operation(summary = "Đặt hàng từ giỏ hiện tại (giá lấy từ database, 1 transaction). VNPay: trả thêm paymentUrl")
    public ResponseEntity<CreateOrderResponse> createOrder(@AuthenticationPrincipal AuthUser user,
                                                           @Valid @RequestBody CreateOrderRequest req,
                                                           HttpServletRequest request) {
        // IP cua khach gui kem cho VNPay (vnp_IpAddr). Chay qua proxy cua Vite thi la IP may minh.
        String clientIp = request.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(user.id(), req, clientIp));
    }

    @GetMapping
    @Operation(summary = "Lịch sử đơn của khách đang đăng nhập (mới nhất trước), lọc theo trạng thái")
    public ResponseEntity<PageResponse<OrderSummaryResponse>> myOrders(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) @Pattern(regexp = CUSTOMER_STATUS_PATTERN) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(orderManagementService.listCustomerOrders(user.id(), status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết 1 đơn của mình (đơn của người khác: 403)")
    public ResponseEntity<OrderDetailResponse> myOrder(@AuthenticationPrincipal AuthUser user,
                                                       @PathVariable long id) {
        return ResponseEntity.ok(orderManagementService.getCustomerOrder(user.id(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Khách hủy đơn (chỉ khi quán chưa nhận đơn), bắt buộc ghi lý do")
    public ResponseEntity<OrderDetailResponse> cancel(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable long id,
                                                      @Valid @RequestBody CancelOrderRequest req) {
        return ResponseEntity.ok(orderManagementService.cancelByCustomer(user.id(), id, req.reason()));
    }
}
