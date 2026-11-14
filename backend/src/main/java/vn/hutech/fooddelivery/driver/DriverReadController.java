package vn.hutech.fooddelivery.driver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.hutech.fooddelivery.common.ApiError;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsResponse;
import vn.hutech.fooddelivery.driver.order.DriverOrderHistoryItem;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/driver")
@Tag(name = "Driver Reports", description = "Thu nhập và lịch sử giao hàng của tài xế hiện tại")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Ngày hoặc phân trang không hợp lệ", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Sai vai trò", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Không có hồ sơ tài xế", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Không thể đọc database", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class DriverReadController {
    private final DriverReadService service;

    public DriverReadController(DriverReadService service) {
        this.service = service;
    }

    @GetMapping("/earnings")
    @Operation(summary = "Đọc thu nhập đã lưu theo ngày Việt Nam",
            description = "from/to dạng yyyy-MM-dd, gồm cả hai ngày. Bỏ một phía là không giới hạn phía đó; "
                    + "bỏ cả hai trả toàn bộ. Tổng và items cùng phạm vi, không tính lại 80% hoặc ghi database.")
    @ApiResponse(responseCode = "200", description = "Tổng và earnings từng đơn (có thể rỗng)")
    public ResponseEntity<DriverEarningsResponse> earnings(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "Ngày đầu, bỏ trống để không giới hạn đầu", schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String from,
            @Parameter(description = "Ngày cuối (bao gồm), bỏ trống để không giới hạn cuối", schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.earnings(user.id(), from, to));
    }

    @GetMapping("/orders/history")
    @Operation(summary = "Lịch sử đơn DELIVERED của tài xế hiện tại",
            description = "Page bắt đầu từ 0; size 1..50, mặc định 10. delivered_at DESC, id DESC, "
                    + "thời gian hiển thị theo Việt Nam; đơn cũ thiếu earnings vẫn được trả.")
    @ApiResponse(responseCode = "200", description = "PageResponse lịch sử, có thể rỗng")
    public ResponseEntity<PageResponse<DriverOrderHistoryItem>> history(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Parameter(schema = @Schema(minimum = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "50")) @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(user.id(), page, size));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> invalidInput(ResponseStatusException failure) {
        int status = failure.getStatusCode().value();
        return ResponseEntity.status(status).body(ApiError.of(status, failure.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseFailure(DataAccessException failure) {
        return ResponseEntity.internalServerError().body(ApiError.of(500, "Không thể tải thu nhập hoặc lịch sử giao hàng. Vui lòng thử lại"));
    }
}
