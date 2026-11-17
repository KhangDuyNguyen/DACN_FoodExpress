package vn.hutech.fooddelivery.driver.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.ApiError;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/driver/orders")
@Tag(name = "Driver Orders", description = "Đơn hàng thuộc tài xế đang đăng nhập")
public class DriverOrderController {
    private final DriverOrderService service;

    public DriverOrderController(DriverOrderService service) {
        this.service = service;
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

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseFailure(DataAccessException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Không thể tải đơn hiện tại. Vui lòng thử lại"));
    }
}
