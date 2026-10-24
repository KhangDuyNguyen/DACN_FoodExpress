package vn.hutech.fooddelivery.common;

/** Nem ra khi da dang nhap nhung khong du quyen; GlobalExceptionHandler doi thanh HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
