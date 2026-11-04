package vn.hutech.fooddelivery.driver.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record DriverStatusRequest(
        @NotNull(message = "Vui lòng gửi isOnline")
        @JsonDeserialize(using = StrictBooleanDeserializer.class)
        Boolean isOnline
) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Chỉ được gửi isOnline");
    }

    /** Khong ep chuoi true/false hay so 0/1 thanh boolean. */
    public static class StrictBooleanDeserializer extends StdDeserializer<Boolean> {

        public StrictBooleanDeserializer() {
            super(Boolean.class);
        }

        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) {
                return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
            }
            return parser.getBooleanValue();
        }
    }
}
