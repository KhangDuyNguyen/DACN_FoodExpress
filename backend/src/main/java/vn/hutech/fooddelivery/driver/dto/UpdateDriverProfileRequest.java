package vn.hutech.fooddelivery.driver.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
@JsonAutoDetect(isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public record UpdateDriverProfileRequest(
        @NotBlank(message = "Vui lòng chọn loại phương tiện")
        @Pattern(regexp = "MOTORBIKE|BICYCLE|CAR", message = "Loại phương tiện phải là MOTORBIKE, BICYCLE hoặc CAR")
        @Schema(allowableValues = {"MOTORBIKE", "BICYCLE", "CAR"})
        String vehicleType,

        @Size(max = 20, message = "Biển số tối đa 20 ký tự")
        @Schema(description = "Bắt buộc với MOTORBIKE/CAR; BICYCLE có thể để trống")
        String licensePlate
) {

    public UpdateDriverProfileRequest {
        licensePlate = licensePlate == null || licensePlate.isBlank() ? null : licensePlate.trim();
    }

    @AssertTrue(message = "MOTORBIKE và CAR bắt buộc có biển số")
    @Schema(hidden = true)
    public boolean isLicensePlateValid() {
        return !("MOTORBIKE".equals(vehicleType) || "CAR".equals(vehicleType)) || licensePlate != null;
    }

    // Chi request nay tu choi truong la; khong doi cau hinh Jackson/API cua cac thanh vien khac.
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Chỉ được gửi vehicleType và licensePlate");
    }
}
