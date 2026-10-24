package vn.hutech.fooddelivery.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cau hinh JWT doc tu app.jwt.* trong application.yml.
 * Neu JWT_SECRET rong hoac ngan hon 32 ky tu, Spring dung khoi dong va bao loi (nho @Validated).
 * HS256 can khoa toi thieu 256 bit = 32 byte.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "Chua dat JWT_SECRET trong file .env")
        @Size(min = 32, message = "JWT_SECRET phai co it nhat 32 ky tu")
        String secret,

        @Min(1)
        long expirationMinutes
) {
}
