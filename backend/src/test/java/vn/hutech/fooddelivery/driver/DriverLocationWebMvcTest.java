package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.hutech.fooddelivery.config.SecurityConfig;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.security.RestAccessDeniedHandler;
import vn.hutech.fooddelivery.security.RestAuthenticationEntryPoint;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DriverController.class, properties = {
        "spring.config.import=", "debug=false",
        "app.jwt.secret=driver_location_test_secret_at_least_32_chars"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        DriverService.class, JwtService.class})
class DriverLocationWebMvcTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    UserRepository userRepository;
    @MockitoBean
    DriverRepository driverRepository;
    @MockitoBean
    DriverLocationRepository driverLocationRepository;

    @ParameterizedTest
    @MethodSource("validCoordinates")
    void acceptsCoordinatesAndUsesDriverIdResolvedFromJwtUser(String body, String lat, String lng) throws Exception {
        String token = authenticate("DRIVER");
        when(driverRepository.findIdByUserIdForUpdate(42)).thenReturn(Optional.of(7L));
        when(driverRepository.updateLocation(eq(7L), any(), any(), any())).thenReturn(1);

        mockMvc.perform(post("/api/driver/location").param("driverId", "999").param("userId", "999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.lat").value(new BigDecimal(lat).doubleValue()))
                .andExpect(jsonPath("$.lng").value(new BigDecimal(lng).doubleValue()))
                .andExpect(jsonPath("$.recordedAt").isString());
        verify(driverRepository).findIdByUserIdForUpdate(42);
        verify(driverLocationRepository).insert(eq(7L), eq(new BigDecimal(lat)), eq(new BigDecimal(lng)), any());
    }

    static Stream<Arguments> validCoordinates() {
        return Stream.of(
                Arguments.of("{\"lat\":10.7769,\"lng\":106.7009}", "10.7769000", "106.7009000"),
                Arguments.of("{\"lat\":-90,\"lng\":-180}", "-90.0000000", "-180.0000000"),
                Arguments.of("{\"lat\":90,\"lng\":180}", "90.0000000", "180.0000000"),
                Arguments.of("{\"lat\":0,\"lng\":0}", "0.0000000", "0.0000000"),
                Arguments.of("{\"lat\":10.776912345,\"lng\":106.70091235}", "10.7769123", "106.7009124")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"lat\":null,\"lng\":106}", "{\"lat\":10,\"lng\":null}",
            "{\"lat\":10}", "{\"lng\":106}",
            "{\"lat\":90.00000001,\"lng\":106}", "{\"lat\":-90.00000001,\"lng\":106}",
            "{\"lat\":10,\"lng\":180.00000001}", "{\"lat\":10,\"lng\":-180.00000001}",
            "{\"lat\":NaN,\"lng\":106}", "{\"lat\":10,\"lng\":Infinity}",
            "{\"lat\":-Infinity,\"lng\":106}", "{\"lat\":\"NaN\",\"lng\":106}",
            "{\"lat\":10,\"lng\":\"Infinity\"}", "{\"lat\":\"10\",\"lng\":106}",
            "{\"lat\":10,\"lng\":true}", "{\"lat\":[],\"lng\":106}",
            "{\"lat\":10,\"lng\":{}}", "{\"lat\":1e309,\"lng\":106}",
            "null", "[]", "{", ""
    })
    void invalidCoordinatesOrJsonReturn400ApiErrorWithoutDatabaseAccess(String body) throws Exception {
        String token = authenticate("DRIVER");

        mockMvc.perform(post("/api/driver/location").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(driverRepository, driverLocationRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"driverId", "userId", "orderId", "order_id", "recordedAt", "timestamp",
            "lastLocationAt", "isOnline", "status", "role"})
    void rejectsSpoofedIdsTimestampsAndOtherFieldsEvenWhenNull(String field) throws Exception {
        String token = authenticate("DRIVER");

        mockMvc.perform(post("/api/driver/location").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":10,\"lng\":106,\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(driverRepository, driverLocationRepository);
    }

    @Test
    void missingDriverProfileReturns404() throws Exception {
        String token = authenticate("DRIVER");
        when(driverRepository.findIdByUserIdForUpdate(42)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/driver/location").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":10,\"lng\":106}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verifyNoInteractions(driverLocationRepository);
    }

    @Test
    void noJwtReturns401() throws Exception {
        mockMvc.perform(post("/api/driver/location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":10,\"lng\":106}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(driverRepository, driverLocationRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "ADMIN"})
    void otherRolesReturn403(String role) throws Exception {
        String token = authenticate(role);

        mockMvc.perform(post("/api/driver/location").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":10,\"lng\":106}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(driverRepository, driverLocationRepository);
    }

    private String authenticate(String role) {
        when(userRepository.findById(42)).thenReturn(Optional.of(new UserRow(
                42, "Driver Test", null, "0911111111", "private-hash", role, "ACTIVE",
                null, null, LocalDateTime.now())));
        return jwtService.generateToken(42, role);
    }
}
