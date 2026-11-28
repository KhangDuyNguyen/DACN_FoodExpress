package vn.hutech.fooddelivery.tracking;

import org.springframework.http.HttpStatus;

final class TrackingRouteException extends RuntimeException {
    enum Reason {
        ROUTE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"),
        ROUTE_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đơn hàng này"),
        ROUTE_ORDER_NOT_READY(HttpStatus.CONFLICT, "Đơn hàng chưa đủ điều kiện tính tuyến"),
        ROUTE_GPS_UNAVAILABLE(HttpStatus.CONFLICT, "Chưa có vị trí tài xế hợp lệ và đủ mới"),
        ROUTE_DESTINATION_INVALID(HttpStatus.CONFLICT, "Điểm giao hàng không hợp lệ"),
        ROUTE_CONTEXT_CHANGED(HttpStatus.CONFLICT, "Thông tin giao hàng đã thay đổi"),
        ROUTE_PROFILE_UNSUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Phương tiện chưa hỗ trợ tính tuyến"),
        ROUTE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "Không tìm được tuyến đường bộ"),
        ROUTE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Đã đến giới hạn tính tuyến"),
        ROUTE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tính tuyến chưa sẵn sàng"),
        ROUTE_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "Dịch vụ bản đồ trả kết quả không hợp lệ"),
        ROUTE_UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Dịch vụ bản đồ phản hồi quá chậm");

        final HttpStatus status;
        final String message;

        Reason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    final Reason reason;

    TrackingRouteException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }
}
