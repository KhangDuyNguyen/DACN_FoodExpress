package vn.hutech.fooddelivery.common;

/**
 * Ném ra khi tham số yêu cầu không hợp lệ (HTTP 400).
 * Kế thừa IllegalArgumentException để tương thích cả hai kiểu ngoại lệ.
 */
public class BadRequestException extends IllegalArgumentException {
/** Nem ra khi du lieu gui len sai ma Bean Validation khong bat duoc; GlobalExceptionHandler doi thanh HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
