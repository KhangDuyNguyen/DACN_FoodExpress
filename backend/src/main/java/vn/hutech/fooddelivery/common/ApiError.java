package vn.hutech.fooddelivery.common;

import java.time.Instant;
import java.util.Map;

/** Dinh dang loi tra ve chung cho moi API. */
public record ApiError(int status, String message, Map<String, String> fieldErrors, Instant timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Map.of(), Instant.now());
    }
}
