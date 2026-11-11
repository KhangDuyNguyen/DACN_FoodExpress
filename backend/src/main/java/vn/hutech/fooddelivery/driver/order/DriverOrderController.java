package vn.hutech.fooddelivery.driver.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.ApiError;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/driver/orders")
@Tag(name = "Driver Orders", description = "Đơn hàng thuộc tài xế đang đăng nhập")
public class DriverOrderController {
    private final DriverOrderService service;
    private final DriverDeliveryService delivery;

    public DriverOrderController(DriverOrderService service, DriverDeliveryService delivery) {
        this.service = service;
        this.delivery = delivery;
    }

    @GetMapping("/current")
    @Operation(summary = "Lấy đơn đang thực hiện của tài xế hiện tại",
            description = "Chỉ đọc theo JWT, không nhận driverId. Trả currentOrder null khi không có đơn hoạt động. "
                    + "Offline hoặc GPS không khả dụng vẫn xem được đơn đã gán.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đơn hiện tại hoặc currentOrder null và giờ Backend",
                    content = @Content(schema = @Schema(implementation = DriverCurrentOrderResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc JWT không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Tài khoản không có vai trò DRIVER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Không có hồ sơ tài xế",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Có nhiều đơn hoạt động gắn cùng tài xế",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Không thể đọc dữ liệu; không phải kết quả không có đơn",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DriverCurrentOrderResponse> current(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getCurrentOrder(user.id()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái giao hàng của đơn đã nhận",
            description = "PREPARING/READY_FOR_PICKUP -> PICKED_UP -> DELIVERING -> DELIVERED. "
                    + "Yêu cầu đúng tài xế và một assignment ACCEPTED. Gửi lại trạng thái hiện tại hợp lệ trả 200 "
                    + "không ghi thêm lịch sử/thu nhập; request cũ sau khi đơn tiến bước trả 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trạng thái đã lưu (bao gồm gửi lặp hợp lệ)",
                    content = @Content(schema = @Schema(implementation = DriverOrderStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID hoặc body không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sai vai trò hoặc đơn thuộc tài xế khác",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Không có hồ sơ tài xế hoặc đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Sai bước, assignment hoặc dữ liệu không nhất quán",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi database; thao tác ghi được rollback",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Cạnh tranh khóa; thao tác ghi được rollback",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DriverOrderStatusResponse> changeStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @PathVariable long id, @Valid @RequestBody ChangeDriverOrderStatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(delivery.changeStatus(user.id(), id, request.status()));
    }

    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<ApiError> lockFailure(TransientDataAccessException failure,
                                        HttpServletRequest request) {
        // Giu HTTP 500 cua GET current, chi PATCH ghi tra 503 khi can retry khoa.
        if (!"PATCH".equals(request.getMethod())) return databaseFailure(failure, request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Chưa thể cập nhật do cạnh tranh dữ liệu. Vui lòng thử lại"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseFailure(DataAccessException failure,
                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "PATCH".equals(request.getMethod())
                        ? "Không thể cập nhật trạng thái giao hàng. Vui lòng thử lại"
                        : "Không thể tải đơn hiện tại. Vui lòng thử lại"));
    }
}
