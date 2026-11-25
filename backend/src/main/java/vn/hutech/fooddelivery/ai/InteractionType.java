package vn.hutech.fooddelivery.ai;

import vn.hutech.fooddelivery.common.BadRequestException;

import java.util.Locale;

/**
 * Các loại hành vi người dùng tương tác với món ăn (khớp ENUM trong bảng user_item_interactions).
 * Mỗi loại có trọng số mặc định phục vụ thuật toán gợi ý món ăn.
 */
public enum InteractionType {
    VIEW(1.0),
    ADD_TO_CART(3.0),
    FAVORITE(3.5),
    ORDER(5.0);

    private final double weight;

    InteractionType(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }

    public static InteractionType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Loại hành vi (type) không được để trống");
        }
        try {
            return InteractionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Loại hành vi không hợp lệ. Chỉ chấp nhận: VIEW, ADD_TO_CART, ORDER, FAVORITE");
        }
    }
}

