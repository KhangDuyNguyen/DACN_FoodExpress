package vn.hutech.fooddelivery.tracking;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/orders")
public class OrderTrackingController {
    private final OrderTrackingService trackingService;

    public OrderTrackingController(OrderTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/{id}/tracking")
    @Operation(summary = "Xem trạng thái và vị trí giao hàng gần nhất của đơn mình đặt",
            description = "deliveryPoint là tọa độ điểm giao của đơn. "
                    + "latestLocation là null khi đơn chưa có vị trí từ tài xế đang được gán.")
    public ResponseEntity<OrderTrackingResponse> getTracking(@AuthenticationPrincipal AuthUser user,
                                                             @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(trackingService.getTracking(user.id(), id));
    }
}
