package vn.hutech.fooddelivery.assignment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.hutech.fooddelivery.common.ApiError;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/driver/offers")
@Tag(name = "Driver Offers", description = "Lời mời giao hàng của tài xế đang đăng nhập")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token không hợp lệ",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Sai vai trò hoặc không sở hữu lời mời",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Không có hồ sơ tài xế hoặc lời mời",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Lời mời/đơn hàng/tài xế không còn hợp lệ",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Lỗi database; thao tác ghi được rollback",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Cạnh tranh khóa; hoặc đã từ chối nhưng bước tìm tài xế tiếp theo lỗi",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class DriverOfferController {

    private final DriverOfferService service;

    public DriverOfferController(DriverOfferService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Lấy lời mời còn hiệu lực của tài xế hiện tại",
            description = "Dùng serverTime và expiresAt để tính countdown. Không trả thông tin liên hệ khách và không ghi database.")
    @ApiResponse(responseCode = "200", description = "Danh sách lời mời còn hiệu lực và giờ Backend",
            content = @Content(schema = @Schema(implementation = DriverOffersResponse.class)))
    public DriverOffersResponse getOffers(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user) {
        return service.getOffers(user.id());
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Nhận lời mời của tài xế hiện tại",
            description = "Nhận đơn PLACED/PREPARING/READY_FOR_PICKUP chưa có tài xế. PLACED chuyển PREPARING; không chuyển lùi. Nhận lặp giữ kết quả khi đơn còn ở giai đoạn này và gắn đúng tài xế.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã nhận lời mời",
                    content = @Content(schema = @Schema(implementation = DriverOfferActionResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DriverOfferActionResponse accept(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                                            @PathVariable long id) {
        return service.accept(user.id(), id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối lời mời và tìm tài xế kế tiếp",
            description = "Không cần request body. Từ chối lặp giữ respondedAt cũ và thử tìm tài xế tiếp theo sau commit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã từ chối và thử tìm tài xế kế tiếp",
                    content = @Content(schema = @Schema(implementation = DriverOfferActionResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DriverOfferActionResponse reject(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                                            @PathVariable long id) {
        return service.reject(user.id(), id);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalidId(Exception failure) {
        return error(HttpStatus.BAD_REQUEST, "offerId phải là số nguyên dương");
    }

    @ExceptionHandler(DriverOfferService.ReassignmentFailedException.class)
    ResponseEntity<ApiError> reassignmentFailed(DriverOfferService.ReassignmentFailedException failure) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, failure.getMessage());
    }

    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<ApiError> lockFailure(TransientDataAccessException failure) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Chưa thể xử lý lời mời do cạnh tranh dữ liệu. Vui lòng thử lại");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseFailure(DataAccessException failure) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý lời mời. Vui lòng thử lại");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), message));
    }
}
