package vn.hutech.fooddelivery.common;

/** Nem ra khi du lieu bi trung hoac xung dot; GlobalExceptionHandler doi thanh HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
