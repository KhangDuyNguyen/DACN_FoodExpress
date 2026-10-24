package vn.hutech.fooddelivery.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Ten security scheme, dung trong nut "Authorize" cua Swagger UI. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI foodDeliveryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Food Delivery API")
                        .version("0.0.1")
                        .description("API he thong giao do an nhanh - do an chuyen nganh"))
                // Bam "Authorize" trong Swagger, dan token (khong can chu "Bearer "), Swagger tu gan vao moi request
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
