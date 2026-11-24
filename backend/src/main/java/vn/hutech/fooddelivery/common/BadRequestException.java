package vn.hutech.fooddelivery.common;

/**
 * Ném ra khi tham số yêu cầu không hợp lệ (HTTP 400).
 * Kế thừa IllegalArgumentException để tương thích cả hai kiểu ngoại lệ.
 */
public class BadRequestException extends IllegalArgumentException {
    public BadRequestException(String message) {
        super(message);
    }
}
