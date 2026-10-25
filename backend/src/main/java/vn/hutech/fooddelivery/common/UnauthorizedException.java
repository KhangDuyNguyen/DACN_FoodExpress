package vn.hutech.fooddelivery.common;

/** Nem ra khi chua dang nhap hoac sai thong tin dang nhap; GlobalExceptionHandler doi thanh HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
