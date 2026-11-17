package vn.hutech.fooddelivery.admin.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.hutech.fooddelivery.admin.dashboard.dto.AdminDashboardStatsResponse;
import vn.hutech.fooddelivery.config.SecurityConfig;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.security.RestAccessDeniedHandler;
import vn.hutech.fooddelivery.security.RestAuthenticationEntryPoint;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminDashboardController.class, properties = {
        "app.jwt.secret=admin_dashboard_test_secret_at_least_32_chars",
        "spring.config.import="
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        AdminDashboardService.class,
        JwtService.class,
        AdminDashboardControllerWebMvcTest.StubConfig.class
})
class AdminDashboardControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @TestConfiguration
    static class StubConfig {
        private static final Map<Long, UserRow> USERS = Map.of(
                1L, new UserRow(1L, "Admin", "admin@test.com", "0900000001", "hash", "ADMIN", "ACTIVE", null, null, LocalDateTime.now()),
                2L, new UserRow(2L, "Merchant", "merchant@test.com", "0900000002", "hash", "MERCHANT", "ACTIVE", null, null, LocalDateTime.now()),
                3L, new UserRow(3L, "Customer", "customer@test.com", "0900000003", "hash", "CUSTOMER", "ACTIVE", null, null, LocalDateTime.now())
        );

        @Bean
        UserRepository userRepository() {
            return new UserRepository(null) {
                @Override
                public Optional<UserRow> findById(long id) {
                    return Optional.ofNullable(USERS.get(id));
                }
            };
        }

        @Bean
        AdminDashboardRepository adminDashboardRepository() {
            return new AdminDashboardRepository(null) {
                @Override
                public AdminDashboardStatsResponse getStats() {
                    return new AdminDashboardStatsResponse(
                            42L,
                            BigDecimal.valueOf(5850000),
                            120L,
                            18L
                    );
                }
            };
        }
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void merchantRoleReturns403() throws Exception {
        String token = jwtService.generateToken(2L, "MERCHANT");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerRoleReturns403() throws Exception {
        String token = jwtService.generateToken(3L, "CUSTOMER");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleReturns200WithStats() throws Exception {
        String token = jwtService.generateToken(1L, "ADMIN");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(42))
                .andExpect(jsonPath("$.totalRevenue").value(5850000))
                .andExpect(jsonPath("$.totalUsers").value(120))
                .andExpect(jsonPath("$.totalRestaurants").value(18));
    }
}

