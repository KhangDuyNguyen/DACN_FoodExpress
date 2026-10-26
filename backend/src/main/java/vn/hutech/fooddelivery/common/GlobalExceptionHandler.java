package vn.hutech.fooddelivery.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Doi moi exception thanh JSON ApiError. Khong bao gio tra stack trace cho client. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), "Dữ liệu không hợp lệ", fields, Instant.now()));
    }

    /** Body khong phai JSON hop le, hoac sai kieu (vi du "quantity": "abc"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Dữ liệu gửi lên không đúng định dạng"));
    }

    /** Tham so tren URL sai kieu, vi du ?page=abc hoac /api/orders/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Tham số '" + ex.getName() + "' không hợp lệ"));
    }

    /** @PreAuthorize tu choi trong controller/service. Neu khong bat rieng se roi xuong handler 500 ben duoi. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), "Bạn không có quyền truy cập"));
    }

    /**
     * Moi loi con lai.
     * Loi co san cua Spring MVC (sai method -> 405, thieu tham so -> 400, sai duong dan -> 404...) deu
     * implement ErrorResponse va tu biet ma HTTP cua minh, nen giu nguyen ma do.
     * Loi that su khong luong truoc -> 500, ghi log day du o server, client chi nhan cau chung.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return ResponseEntity.status(status).body(ApiError.of(status, messageFor(status)));
        }
        log.error("Loi khong luong truoc", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi hệ thống, vui lòng thử lại sau"));
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 400 -> "Yêu cầu không hợp lệ";
            case 404 -> "Không tìm thấy đường dẫn";
            case 405 -> "Phương thức không được hỗ trợ";
            case 415 -> "Kiểu dữ liệu không được hỗ trợ";
            default -> "Yêu cầu không thành công";
        };
    }
}
