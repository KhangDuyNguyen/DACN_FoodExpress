package vn.hutech.fooddelivery.common;

/** Nem ra khi khong tim thay du lieu; GlobalExceptionHandler doi thanh HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
