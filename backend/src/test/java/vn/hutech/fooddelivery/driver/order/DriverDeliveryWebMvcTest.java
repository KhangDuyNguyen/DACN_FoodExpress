package vn.hutech.fooddelivery.driver.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.hutech.fooddelivery.config.SecurityConfig;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsRepository;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.security.*;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DriverOrderController.class, properties = {
        "spring.config.import=", "debug=false", "app.jwt.secret=0123456789abcdef0123456789abcdef",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        DriverDeliveryService.class, JwtService.class})
class DriverDeliveryWebMvcTest {
    private static final String URL = "/api/driver/orders/700301/status";
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean DriverOrderService current;
    @MockitoBean DriverDeliveryRepository deliveries;
    @MockitoBean OrderRepository orders;
    @MockitoBean DriverEarningsRepository earnings;

    private String token(String role) {
        when(users.findById(700042)).thenReturn(Optional.of(new UserRow(700042, "Delivery MVC", null,
                "0911111111", "unused", role, "ACTIVE", null, null, LocalDateTime.now())));
        return jwt.generateToken(700042, role);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"\"}", "{\"status\":\"picked_up\"}",
            "{\"status\":\"PREPARING\"}", "{\"status\":\"CANCELLED\"}", "{\"status\":1}",
            "{\"status\":[]}", "{", "[]", "null", ""})
    void invalidRequestIs400WithoutBusinessReads(String body) throws Exception {
        mvc.perform(patch(URL).header("Authorization", "Bearer " + token("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap()).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"driverId", "userId", "amount", "bonus", "role"})
    void clientCannotSupplyIdentityOrMoney(String field) throws Exception {
        mvc.perform(patch(URL).header("Authorization", "Bearer " + token("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\",\"" + field + "\":8}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidPathIs400(String id) throws Exception {
        mvc.perform(patch("/api/driver/orders/{id}/status", id).header("Authorization", "Bearer " + token("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"", "Bearer invalid.jwt.token"})
    void missingOrInvalidJwtIs401(String authorization) throws Exception {
        mvc.perform(patch(URL).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "MERCHANT", "ADMIN"})
    void nonDriverIs403(String role) throws Exception {
        mvc.perform(patch(URL).header("Authorization", "Bearer " + token(role)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @Test void expiredJwtIs401() throws Exception {
        var expired = new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", -1));
        mvc.perform(patch(URL).header("Authorization", "Bearer " + expired.generateToken(700042, "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @Test void requestUsesJwtIdentityAndReturnsPersistedReplay() throws Exception {
        String jwtToken = token("DRIVER");
        var now = LocalDateTime.of(2026, 11, 11, 12, 0);
        when(deliveries.findDriverId(700042)).thenReturn(Optional.of(700071L));
        when(deliveries.lockOrder(700301)).thenReturn(Optional.of(new DriverDeliveryRepository.DeliveryOrder(
                700301, 700071L, "PICKED_UP", BigDecimal.ZERO, now, null)));
        when(deliveries.lockDriver(700071, 700042)).thenReturn(true);
        when(deliveries.lockAcceptedDriverIds(700301)).thenReturn(List.of(700071L));
        when(deliveries.hasDriverHistory(700301, "PICKED_UP", 700042)).thenReturn(true);
        mvc.perform(patch(URL).param("userId", "8").param("driverId", "8")
                        .header("Authorization", "Bearer " + jwtToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orderId").value(700301)).andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-11T12:00:00"));
        verify(deliveries).findDriverId(700042);
        verifyNoInteractions(orders, current);
    }

    @Test void missingProfileIs404() throws Exception {
        mvc.perform(patch(URL).header("Authorization", "Bearer " + token("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sqlAndLockFailuresAreApiErrors(boolean transientFailure) throws Exception {
        when(deliveries.findDriverId(700042)).thenThrow(transientFailure
                ? new CannotAcquireLockException("private SQL") : new DataIntegrityViolationException("private SQL"));
        int expected = transientFailure ? 503 : 500;
        mvc.perform(patch(URL).header("Authorization", "Bearer " + token("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test void currentOrderKeepsExisting500ForTransientDatabaseFailure() throws Exception {
        when(current.getCurrentOrder(700042)).thenThrow(new CannotAcquireLockException("private SQL"));
        mvc.perform(get("/api/driver/orders/current").header("Authorization", "Bearer " + token("DRIVER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Không thể tải đơn hiện tại. Vui lòng thử lại"));
    }
}
