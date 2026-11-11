package vn.hutech.fooddelivery.driver.order;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ChangeDriverOrderStatusRequest(
        @NotBlank(message = "Vui lòng chọn trạng thái giao hàng")
        @Pattern(regexp = "PICKED_UP|DELIVERING|DELIVERED",
                message = "Trạng thái phải là PICKED_UP, DELIVERING hoặc DELIVERED")
        @Schema(allowableValues = {"PICKED_UP", "DELIVERING", "DELIVERED"}) String status
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Chỉ được gửi status");
    }
}
