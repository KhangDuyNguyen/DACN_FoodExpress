package vn.hutech.fooddelivery.common;

/** Nem ra khi du lieu gui len sai ma Bean Validation khong bat duoc; GlobalExceptionHandler doi thanh HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
