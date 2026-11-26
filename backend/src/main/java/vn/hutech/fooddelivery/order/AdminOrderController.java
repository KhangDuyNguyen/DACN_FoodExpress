package vn.hutech.fooddelivery.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.order.dto.AdminOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;

import java.time.LocalDate;

/** Admin xem don toan san. /api/admin/** chi ADMIN goi duoc (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "Admin - Đơn toàn sàn", description = "Xem và lọc mọi đơn hàng trên hệ thống")
public class AdminOrderController {

    private final OrderManagementService orderManagementService;

    public AdminOrderController(OrderManagementService orderManagementService) {
        this.orderManagementService = orderManagementService;
    }

    @GetMapping
    @Operation(summary = "Đơn toàn sàn, mới nhất trước; lọc trạng thái, quán, cách thanh toán, ngày đặt; q = đầu mã đơn hoặc SĐT khách")
    public ResponseEntity<PageResponse<AdminOrderSummaryResponse>> list(
            @RequestParam(required = false) @Pattern(regexp = OrderController.CUSTOMER_STATUS_PATTERN) String status,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) @Pattern(regexp = "^$|COD|VNPAY|MOMO") String paymentMethod,
            @RequestParam(required = false) @Size(max = 30) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        AdminOrderFilter filter = new AdminOrderFilter(blankToNull(status), restaurantId, blankToNull(paymentMethod),
                blankToNull(q), from, to);
        return ResponseEntity.ok(orderManagementService.listAdminOrders(filter, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết 1 đơn bất kỳ: món, tiền, tài xế, lịch sử trạng thái")
    public ResponseEntity<OrderDetailResponse> detail(@PathVariable long id) {
        return ResponseEntity.ok(orderManagementService.getAdminOrder(id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
