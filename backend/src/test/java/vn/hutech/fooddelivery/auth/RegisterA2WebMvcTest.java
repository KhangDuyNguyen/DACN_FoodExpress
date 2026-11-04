package vn.hutech.fooddelivery.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.hutech.fooddelivery.auth.dto.AuthResponse;
import vn.hutech.fooddelivery.auth.dto.RegisterRequest;
import vn.hutech.fooddelivery.config.SecurityConfig;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.security.RestAccessDeniedHandler;
import vn.hutech.fooddelivery.security.RestAuthenticationEntryPoint;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test validation Register A2 cua An; su dung AuthController/SecurityConfig hien co. */
@WebMvcTest(controllers = AuthController.class, properties = {
        "spring.config.import=", "debug=false", "app.jwt.secret=register_a2_test_secret_at_least_32_chars"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class RegisterA2WebMvcTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    UserRepository users;

    @ParameterizedTest
    @MethodSource("validDriverVehicles")
    void driverCanRegisterWithRequiredVehicleRules(String vehicle, String plate, String normalizedPlate) throws Exception {
        var body = account("DRIVER");
        body.put("vehicleType", vehicle);
        body.put("licensePlate", plate);
        givenSuccess("DRIVER");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("test-jwt"))
                .andExpect(jsonPath("$.user.role").value("DRIVER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());
        var request = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(request.capture());
        assertEquals(vehicle, request.getValue().vehicleType());
        assertEquals(normalizedPlate, request.getValue().licensePlate());
    }

    static Stream<Arguments> validDriverVehicles() {
        return Stream.of(
                Arguments.of("MOTORBIKE", " 59A1-12345 ", "59A1-12345"),
                Arguments.of("CAR", "51A-12345", "51A-12345"),
                Arguments.of("BICYCLE", null, null), Arguments.of("BICYCLE", "", null),
                Arguments.of("BICYCLE", "   ", null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"vehicleType\":null", "\"vehicleType\":\"\"", "\"vehicleType\":\"TRUCK\"",
            "\"vehicleType\":\"motorbike\"", "\"vehicleType\":\"MOTORBIKE\"", "\"vehicleType\":\"CAR\"",
            "\"vehicleType\":\"MOTORBIKE\",\"licensePlate\":null",
            "\"vehicleType\":\"CAR\",\"licensePlate\":\"   \"",
            "\"vehicleType\":\"MOTORBIKE\",\"licensePlate\":\"\"",
            "\"vehicleType\":\"BICYCLE\",\"licensePlate\":\"123456789012345678901\""})
    void invalidDriverVehicleIsRejectedBeforeService(String vehicleFields) throws Exception {
        String base = mapper.writeValueAsString(account("DRIVER"));
        String body = base.substring(0, base.length() - 1) + (vehicleFields.isEmpty() ? "" : "," + vehicleFields) + "}";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT"})
    void oldAccountOnlyPayloadStillRegistersWithoutVehicle(String role) throws Exception {
        givenSuccess(role);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(account(role))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value(role));
        var request = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(request.capture());
        assertEquals(null, request.getValue().vehicleType());
        assertEquals(null, request.getValue().licensePlate());
    }

    @ParameterizedTest
    @MethodSource("invalidAccounts")
    void oldAccountValidationAndAdminRejectionRemainEffective(String field, Object value) throws Exception {
        var body = account("DRIVER");
        body.put("vehicleType", "BICYCLE");
        body.put(field, value);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(authService);
    }

    static Stream<Arguments> invalidAccounts() {
        return Stream.of(Arguments.of("fullName", " "), Arguments.of("fullName", "a".repeat(101)),
                Arguments.of("phone", "123"), Arguments.of("phone", null), Arguments.of("email", "not-an-email"),
                Arguments.of("password", "short"), Arguments.of("password", null),
                Arguments.of("password", "x".repeat(73)), Arguments.of("role", "ADMIN"));
    }

    @Test
    void omittedOptionalEmailAndBicyclePlateAreAccepted() throws Exception {
        var body = account("DRIVER");
        body.put("vehicleType", "BICYCLE");
        givenSuccess("DRIVER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> account(String role) {
        var body = new HashMap<String, Object>();
        body.put("fullName", "Driver Test");
        body.put("phone", "0911111111");
        body.put("password", "matkhau123");
        body.put("role", role);
        return body;
    }

    private void givenSuccess(String role) {
        when(authService.register(any())).thenReturn(new AuthResponse("test-jwt",
                new UserResponse(7, "Driver Test", "0911111111", null, role, "ACTIVE", null, LocalDateTime.now())));
    }
}
