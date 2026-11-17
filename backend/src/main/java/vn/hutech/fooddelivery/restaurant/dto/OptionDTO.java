package vn.hutech.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Một lựa chọn trong nhóm tùy chọn.
 * Ví dụ: size "Nhỏ" +0đ, "Vừa" +5000đ, "Lớn" +10000đ.
 * @JsonProperty bắt buộc vì Jackson serialize boolean isXxx thành "xxx" (bỏ prefix is).
 */
public record OptionDTO(
        long id,
        String name,
        long extraPrice,
        @JsonProperty("isAvailable") boolean isAvailable,
        int sortOrder
) {}
