package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DriverController.class, properties = {
        "spring.config.import=", "debug=false", "app.jwt.secret=driver_status_test_secret_at_least_32_chars"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        DriverService.class, JwtService.class})
class DriverStatusWebMvcTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwt;
    @MockitoBean
    UserRepository users;
    @MockitoBean
    DriverRepository drivers;
    @MockitoBean
    DriverLocationRepository locations;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validBooleanUpdatesOnlyDriverResolvedFromJwtAndReturnsStoredState(boolean online) throws Exception {
        String token = authenticate("DRIVER");
        when(drivers.findStatusByUserIdForUpdate(42)).thenReturn(Optional.of(
                new DriverRepository.DriverStatusRow(7, "APPROVED", !online, BigDecimal.ZERO,
                        BigDecimal.ZERO, LocalDateTime.now().minusSeconds(1))));
        when(drivers.updateOnline(7, online)).thenReturn(1);

        mvc.perform(patch("/api/driver/status").param("userId", "999").param("driverId", "999")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isOnline\":" + online + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.isOnline").value(online));
        verify(drivers).findStatusByUserIdForUpdate(42);
        verify(drivers).updateOnline(7, online);
        verifyNoInteractions(locations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"isOnline\":null}", "{\"isOnline\":\"true\"}",
            "{\"isOnline\":\"false\"}", "{\"isOnline\":1}", "{\"isOnline\":0}",
            "{\"isOnline\":1.0}", "{\"isOnline\":[]}", "{\"isOnline\":{}}", "null", "[]", "{", ""})
    void missingOrNonBooleanInputReturns400ApiError(String body) throws Exception {
        String token = authenticate("DRIVER");
        mvc.perform(patch("/api/driver/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(drivers, locations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"driverId", "userId", "status", "role", "gpsValid", "lat", "lng",
            "lastLocationAt", "is_online", "online"})
    void extraFieldsAreRejectedEvenWhenNull(String field) throws Exception {
        String token = authenticate("DRIVER");
        mvc.perform(patch("/api/driver/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isOnline\":true,\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(drivers, locations);
    }

    @Test
    void noJwtReturns401() throws Exception {
        mvc.perform(patch("/api/driver/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isOnline\":false}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(drivers, locations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "ADMIN"})
    void otherRolesReturn403(String role) throws Exception {
        String token = authenticate(role);
        mvc.perform(patch("/api/driver/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isOnline\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(drivers, locations);
    }

    @Test
    void noDriverProfileReturns404() throws Exception {
        String token = authenticate("DRIVER");
        when(drivers.findStatusByUserIdForUpdate(42)).thenReturn(Optional.empty());
        mvc.perform(patch("/api/driver/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isOnline\":false}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void databaseFailureReturns500ApiErrorWithoutSqlDetails() throws Exception {
        String token = authenticate("DRIVER");
        when(drivers.findStatusByUserIdForUpdate(42)).thenThrow(new DataIntegrityViolationException("private SQL"));
        mvc.perform(patch("/api/driver/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isOnline\":false}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Không thể lưu trạng thái nhận đơn. Vui lòng thử lại"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private String authenticate(String role) {
        when(users.findById(42)).thenReturn(Optional.of(new UserRow(42, "Status Test", null,
                "0911111111", "private-hash", role, "ACTIVE", null, null, LocalDateTime.now())));
        return jwt.generateToken(42, role);
    }
}
