package vn.hutech.fooddelivery.tracking;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.ApiError;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
public class OrderTrackingRouteController {
    private final OrderTrackingRouteService service;

    public OrderTrackingRouteController(OrderTrackingRouteService service) {
        this.service = service;
    }

    @GetMapping("/api/orders/{id}/tracking/route")
    @Operation(summary = "Tính tuyến đường tới điểm giao cho khách sở hữu đơn đang giao",
            description = "Dùng GPS mới nhất của tài xế được gán. Tọa độ GeoJSON theo thứ tự [lng, lat].")
    public ResponseEntity<OrderTrackingRouteResponse> getRoute(@AuthenticationPrincipal AuthUser user,
                                                              @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getRoute(user.id(), id));
    }

    @ExceptionHandler(TrackingRouteException.class)
    ResponseEntity<RouteError> routeError(TrackingRouteException exception) {
        var reason = exception.reason;
        return ResponseEntity.status(reason.status).cacheControl(CacheControl.noStore())
                .body(new RouteError(reason.name(), ApiError.of(reason.status.value(), reason.message)));
    }

    record RouteError(String code, @JsonUnwrapped ApiError error) {
    }
}
