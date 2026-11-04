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
import vn.hutech.fooddelivery.driver.dto.DriverProfileResponse;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.security.RestAccessDeniedHandler;
import vn.hutech.fooddelivery.security.RestAuthenticationEntryPoint;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JWT va SecurityConfig that; mock du lieu de test HTTP khong can MySQL/Docker. */
@WebMvcTest(controllers = DriverController.class, properties = {
        "app.jwt.secret=driver_profile_test_secret_at_least_32_chars",
        "spring.config.import=",
        "debug=false"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        DriverService.class, JwtService.class})
class DriverProfileWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    DriverRepository driverRepository;

    @MockitoBean
    DriverLocationRepository driverLocationRepository;

    @Autowired
    JwtService jwtService;

    @Test
    void validDriverJwtReturnsOnlyOwnProfileAndSafeCamelCaseFields() throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(
                new DriverProfileResponse("Nguyen Van An", "0911111111", "an@example.com",
                        "MOTORBIKE", "59A1-12345", "APPROVED", true)));

        mockMvc.perform(get("/api/driver/profile")
                        .param("userId", "999").param("driverId", "999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(7)))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van An"))
                .andExpect(jsonPath("$.phone").value("0911111111"))
                .andExpect(jsonPath("$.email").value("an@example.com"))
                .andExpect(jsonPath("$.vehicleType").value("MOTORBIKE"))
                .andExpect(jsonPath("$.licensePlate").value("59A1-12345"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.isOnline").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(driverRepository).findProfileByUserId(42);
    }

    @Test
    void pendingDriverCanReadProfileWithNullableEmailAndLicensePlate() throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "PENDING");
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(
                new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                        "BICYCLE", null, "PENDING", false)));

        mockMvc.perform(get("/api/driver/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.isOnline").value(false))
                .andExpect(jsonPath("$.vehicleType").value("BICYCLE"))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.licensePlate").isEmpty());
    }

    @Test
    void missingTokenReturns401ApiError() throws Exception {
        mockMvc.perform(get("/api/driver/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(driverRepository);
    }

    @Test
    void invalidTokenReturns401WithoutReadingProfile() throws Exception {
        mockMvc.perform(get("/api/driver/profile").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(driverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "ADMIN"})
    void otherRolesReturn403WithoutReadingProfile(String role) throws Exception {
        String token = authenticate(role, role, "ACTIVE");

        mockMvc.perform(get("/api/driver/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(driverRepository);
    }

    @Test
    void databaseRoleOverridesDriverRoleInJwt() throws Exception {
        String token = authenticate("DRIVER", "CUSTOMER", "ACTIVE");

        mockMvc.perform(get("/api/driver/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(driverRepository);
    }

    @Test
    void driverWithoutProfileReturns404ApiError() throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/driver/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Không tìm thấy hồ sơ tài xế"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest
    @MethodSource("validUpdates")
    void pendingDriverCanUpdateOnlyOwnVehicle(String body, String vehicleType, String licensePlate) throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        var before = new DriverProfileResponse("Nguyen Van An", "0911111111", "an@example.com",
                "MOTORBIKE", "59A1-12345", "PENDING", false);
        var after = new DriverProfileResponse("Nguyen Van An", "0911111111", "an@example.com",
                vehicleType, licensePlate, "PENDING", false);
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(before), Optional.of(after));
        when(driverRepository.updatePendingProfile(42, vehicleType, licensePlate)).thenReturn(1);

        var result = mockMvc.perform(put("/api/driver/profile")
                        .param("userId", "999").param("driverId", "999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(7)))
                .andExpect(jsonPath("$.vehicleType").value(vehicleType))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.isOnline").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        if (licensePlate == null) {
            result.andExpect(jsonPath("$.licensePlate").value(nullValue()));
        } else {
            result.andExpect(jsonPath("$.licensePlate").value(licensePlate));
        }
        verify(driverRepository).updatePendingProfile(42, vehicleType, licensePlate);
    }

    static Stream<Arguments> validUpdates() {
        return Stream.of(
                Arguments.of("{\"vehicleType\":\"BICYCLE\"}", "BICYCLE", null),
                Arguments.of("{\"vehicleType\":\"BICYCLE\",\"licensePlate\":null}", "BICYCLE", null),
                Arguments.of("{\"vehicleType\":\"BICYCLE\",\"licensePlate\":\"  \"}", "BICYCLE", null),
                Arguments.of("{\"vehicleType\":\"MOTORBIKE\",\"licensePlate\":\" 59A1-12345 \"}",
                        "MOTORBIKE", "59A1-12345"),
                Arguments.of("{\"vehicleType\":\"CAR\",\"licensePlate\":\"51A-12345\"}", "CAR", "51A-12345")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"vehicleType\":null}", "{\"vehicleType\":\"\"}",
            "{\"vehicleType\":\"TRUCK\"}", "{\"vehicleType\":\"bicycle\"}",
            "{\"vehicleType\":\"MOTORBIKE\"}", "{\"vehicleType\":\"CAR\",\"licensePlate\":null}",
            "{\"vehicleType\":\"MOTORBIKE\",\"licensePlate\":\"   \"}",
            "{\"vehicleType\":\"CAR\",\"licensePlate\":\"\"}",
            "{\"vehicleType\":\"BICYCLE\",\"licensePlate\":\"123456789012345678901\"}"
    })
    void invalidVehicleDataReturns400ApiErrorWithoutAccessingProfile(String body) throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(driverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "isOnline", "role", "userId", "driverId", "fullName", "phone", "email",
            "licensePlateValid"})
    void forbiddenOrUnknownFieldReturns400EvenWhenNull(String field) throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        String body = "{\"vehicleType\":\"BICYCLE\",\"" + field + "\":null}";

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap());
        verifyNoInteractions(driverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "", "{\"vehicleType\":{}}",
            "{\"vehicleType\":\"BICYCLE\",\"licensePlate\":{}}"})
    void malformedBodyReturns400ApiError(String body) throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(driverRepository);
    }

    @Test
    void putWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/driver/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"BICYCLE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(driverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "ADMIN"})
    void putByOtherRolesReturns403(String role) throws Exception {
        String token = authenticate(role, role, "ACTIVE");

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleType\":\"BICYCLE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(driverRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "SUSPENDED"})
    void putByNonPendingDriverReturns403WithoutWriting(String driverStatus) throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(
                new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                        "MOTORBIKE", "59A1-12345", driverStatus, false)));

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleType\":\"BICYCLE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(driverRepository, never()).updatePendingProfile(anyLong(), anyString(), nullable(String.class));
    }

    @Test
    void putWithoutProfileReturns404WithoutWriting() throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleType\":\"BICYCLE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(driverRepository, never()).updatePendingProfile(anyLong(), anyString(), nullable(String.class));
    }

    @Test
    void concurrentStatusChangeReturns409ApiError() throws Exception {
        String token = authenticate("DRIVER", "DRIVER", "ACTIVE");
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(
                new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                        "MOTORBIKE", "59A1-12345", "PENDING", false)));
        when(driverRepository.updatePendingProfile(42, "BICYCLE", null)).thenReturn(0);

        mockMvc.perform(put("/api/driver/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleType\":\"BICYCLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private String authenticate(String tokenRole, String databaseRole, String userStatus) {
        String token = jwtService.generateToken(42, tokenRole);
        when(userRepository.findById(42)).thenReturn(Optional.of(new UserRow(
                42, "Nguyen Van An", "an@example.com", "0911111111", "private-password-hash",
                databaseRole, userStatus, null, null, LocalDateTime.now())));
        return token;
    }
}
