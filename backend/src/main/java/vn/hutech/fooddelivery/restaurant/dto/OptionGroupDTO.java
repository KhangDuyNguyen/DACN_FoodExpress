package vn.hutech.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Nhóm tùy chọn của một món ăn.
 * Ví dụ: "Chọn size", "Thêm topping", "Mức đường".
 * @JsonProperty bắt buộc vì Jackson serialize boolean isXxx thành "xxx" (bỏ prefix is).
 */
public record OptionGroupDTO(
        long id,
        String name,
        @JsonProperty("isRequired") boolean isRequired,
        int minSelect,
        int maxSelect,
        int sortOrder,
        List<OptionDTO> options
) {}
