package vn.hutech.fooddelivery.driver;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.ApiError;
import vn.hutech.fooddelivery.driver.dto.DriverProfileResponse;
import vn.hutech.fooddelivery.driver.dto.UpdateDriverProfileRequest;
import vn.hutech.fooddelivery.driver.dto.DriverLocationRequest;
import vn.hutech.fooddelivery.driver.dto.DriverLocationResponse;
import vn.hutech.fooddelivery.driver.dto.DriverStatusRequest;
import vn.hutech.fooddelivery.driver.dto.DriverStatusResponse;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/driver")
@Tag(name = "Driver", description = "Hồ sơ tài xế")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Xem hồ sơ tài xế đang đăng nhập",
            description = "Chỉ dành cho DRIVER, kể cả tài xế đang chờ xét duyệt (PENDING).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hồ sơ tài xế"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Tài khoản không có vai trò DRIVER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ tài xế",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DriverProfileResponse> profile(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(driverService.getProfile(user.id()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật phương tiện của tài xế PENDING",
            description = "Chỉ nhận vehicleType và licensePlate. APPROVED, REJECTED, SUSPENDED không được cập nhật.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hồ sơ sau cập nhật"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc có trường không được phép",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sai vai trò hoặc hồ sơ không ở trạng thái PENDING",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ tài xế",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Hồ sơ thay đổi đồng thời; cần tải lại",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DriverProfileResponse> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody UpdateDriverProfileRequest request) {
        return ResponseEntity.ok(driverService.updateProfile(user.id(), request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidProfileBody(HttpMessageNotReadableException ex,
                                                            HttpServletRequest request) {
        String message = request.getRequestURI().endsWith("/location")
                ? "JSON không hợp lệ. Chỉ được gửi lat và lng dưới dạng số hữu hạn"
                : request.getRequestURI().endsWith("/status")
                ? "JSON không hợp lệ. Chỉ được gửi isOnline dưới dạng boolean"
                : "JSON không hợp lệ. Chỉ được gửi vehicleType và licensePlate";
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
                message));
    }

    @PostMapping("/location")
    @Operation(summary = "Gửi vị trí tài xế đang đăng nhập",
            description = "Nhận lat/lng, lưu lịch sử và vị trí hiện tại. PENDING hoặc Offline vẫn gửi được; không đổi trạng thái, không gắn đơn hàng.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã lưu vị trí",
                    content = @Content(schema = @Schema(implementation = DriverLocationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tọa độ hoặc JSON không hợp lệ, có trường không được phép",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Tài khoản không có vai trò DRIVER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ tài xế",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi lưu vị trí, transaction được rollback",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<?> recordLocation(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody DriverLocationRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(driverService.recordLocation(user.id(), request));
        } catch (DataAccessException ex) {
            // Service da rollback truoc khi tra loi; khong lo SQL hay thong tin ket noi cho client.
            return ResponseEntity.internalServerError().body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Không thể lưu vị trí. Vui lòng thử lại"));
        }
    }

    @PatchMapping("/status")
    @Operation(summary = "Bật/tắt nhận đơn của tài xế đang đăng nhập",
            description = "Online: APPROVED, tọa độ hợp lệ, thời gian vị trí từ 0 đến 60 giây trước (gồm 60), theo giờ Backend đến giây. Offline không yêu cầu GPS; chuyển Online sang Offline bị từ chối khi có đơn hoạt động. OFF lặp khi đã Offline vẫn idempotent. Không tự động Offline khi mất kết nối.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trạng thái nhận đơn đã lưu",
                    content = @Content(schema = @Schema(implementation = DriverStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Thiếu boolean hoặc JSON có trường không được phép",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sai vai trò hoặc tài xế chưa được duyệt để Online",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ tài xế",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Vị trí thiếu, không hợp lệ, cũ quá 60 giây hoặc trong tương lai; hoặc đang có đơn hoạt động khi chuyển Offline",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Lỗi lưu trạng thái",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<?> updateStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody DriverStatusRequest request) {
        try {
            return ResponseEntity.ok(driverService.updateStatus(user.id(), request));
        } catch (DataAccessException ex) {
            return ResponseEntity.internalServerError().body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Không thể lưu trạng thái nhận đơn. Vui lòng thử lại"));
        }
    }
}
