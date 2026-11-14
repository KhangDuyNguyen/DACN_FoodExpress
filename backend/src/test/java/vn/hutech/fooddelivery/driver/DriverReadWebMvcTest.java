package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.hutech.fooddelivery.config.SecurityConfig;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsReadRepository;
import vn.hutech.fooddelivery.driver.order.DriverOrderRepository;
import vn.hutech.fooddelivery.security.*;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DriverReadController.class, properties = {
        "spring.config.import=", "debug=false", "app.jwt.secret=0123456789abcdef0123456789abcdef",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        DriverReadService.class, JwtService.class})
class DriverReadWebMvcTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean DriverOrderRepository orders;
    @MockitoBean DriverEarningsReadRepository earnings;

    private String token(String role) {
        when(users.findById(42)).thenReturn(Optional.of(new UserRow(42, "Read MVC", null, "0911111111", "unused",
                role, "ACTIVE", null, null, LocalDateTime.now())));
        return "Bearer " + jwt.generateToken(42, role);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/driver/earnings", "/api/driver/orders/history"})
    void bothEndpointsRequireValidJwt(String path) throws Exception {
        for (String auth : new String[]{"", "Bearer bad.jwt.token"}) {
            mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
        verifyNoInteractions(orders, earnings);
    }

    @ParameterizedTest @CsvSource({"CUSTOMER,/api/driver/earnings", "MERCHANT,/api/driver/earnings", "ADMIN,/api/driver/earnings",
            "CUSTOMER,/api/driver/orders/history", "MERCHANT,/api/driver/orders/history", "ADMIN,/api/driver/orders/history"})
    void otherRolesCannotReadDriverData(String role, String path) throws Exception {
        mvc.perform(get(path).header("Authorization", token(role))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(orders, earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/driver/earnings", "/api/driver/orders/history"})
    void expiredJwtIs401(String path) throws Exception {
        var expired = new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", -1));
        mvc.perform(get(path).header("Authorization", "Bearer " + expired.generateToken(42, "DRIVER")))
                .andExpect(status().isUnauthorized());
    }

    @Test void earningsDefaultsToUnboundedAndCannotBeRedirectedByClientDriverId() throws Exception {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        mvc.perform(get("/api/driver/earnings").param("driverId", "8").param("userId", "999")
                        .header("Authorization", token("DRIVER")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.from").isEmpty()).andExpect(jsonPath("$.to").isEmpty())
                .andExpect(jsonPath("$.baseAmount").value(0)).andExpect(jsonPath("$.bonus").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0)).andExpect(jsonPath("$.items").isEmpty());
        verify(earnings).findEarnings(7, null, null);
    }

    @Test void historyDefaultsAndOwnershipComeOnlyFromJwt() throws Exception {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        mvc.perform(get("/api/driver/orders/history").param("driverId", "8").header("Authorization", token("DRIVER")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(0)).andExpect(jsonPath("$.items").isEmpty());
        verify(orders).findDeliveredHistory(7, 10, 0);
        verifyNoInteractions(earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"", "2026-02-30", "2026-2-01", "wrong", "2026-11-11T00:00:00"})
    void badDateIs400ApiError(String date) throws Exception {
        mvc.perform(get("/api/driver/earnings").param("from", date).header("Authorization", token("DRIVER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap()).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(orders, earnings);
    }

    @Test void reversedDatesAre400() throws Exception {
        mvc.perform(get("/api/driver/earnings").param("from", "2026-11-12").param("to", "2026-11-11")
                        .header("Authorization", token("DRIVER"))).andExpect(status().isBadRequest());
        verifyNoInteractions(orders, earnings);
    }

    @ParameterizedTest @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,51", "size,-1", "size,abc"})
    void badPaginationIs400(String param, String value) throws Exception {
        mvc.perform(get("/api/driver/orders/history").param(param, value).header("Authorization", token("DRIVER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(orders, earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/driver/earnings", "/api/driver/orders/history"})
    void missingProfileIs404(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", token("DRIVER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest @ValueSource(strings = {"/api/driver/earnings", "/api/driver/orders/history"})
    void sqlFailureIs500AndDoesNotBecomeAnEmptySuccess(String path) throws Exception {
        when(orders.findDriverId(42)).thenThrow(new DataIntegrityViolationException("private SQL"));
        mvc.perform(get(path).header("Authorization", token("DRIVER")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
