package vn.hutech.fooddelivery.delivery.dto;

import jakarta.validation.constraints.NotNull;

/** Admin bat (true) hoac tat (false) 1 quy tac phi giao. */
public record ChangeRuleActiveRequest(
        @NotNull(message = "Vui lòng chọn bật hoặc tắt")
        Boolean active
) {
}
