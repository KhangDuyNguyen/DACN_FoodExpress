package vn.hutech.fooddelivery.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.hutech.fooddelivery.config.SecurityConfig;
import vn.hutech.fooddelivery.order.dto.MerchantOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderItemResponse;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.security.RestAccessDeniedHandler;
import vn.hutech.fooddelivery.security.RestAuthenticationEntryPoint;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MerchantOrderController.class, properties = {
        "app.jwt.secret=merchant_orders_test_secret_at_least_32_chars",
        "spring.config.import="
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        OrderManagementService.class,
        JwtService.class,
        MerchantOrderControllerWebMvcTest.StubConfig.class
})
class MerchantOrderControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @TestConfiguration
    static class StubConfig {
        private static final Map<Long, UserRow> USERS = Map.of(
                10L, new UserRow(10L, "Merchant A", "ma@test.com", "0922000001", "hash", "MERCHANT", "ACTIVE", null, null, LocalDateTime.now()),
                20L, new UserRow(20L, "Customer", "cust@test.com", "0911000001", "hash", "CUSTOMER", "ACTIVE", null, null, LocalDateTime.now()),
                30L, new UserRow(30L, "Admin", "admin@test.com", "0900000001", "hash", "ADMIN", "ACTIVE", null, null, LocalDateTime.now())
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
        OrderRepository orderRepository() {
            return new OrderRepository(null) {
                @Override
                public boolean restaurantExists(long restaurantId) {
                    return restaurantId == 1L || restaurantId == 2L;
                }

                @Override
                public boolean isRestaurantOwnedBy(long restaurantId, long ownerUserId) {
                    return restaurantId == 1L && ownerUserId == 10L;
                }

                @Override
                public List<MerchantOrderSummaryResponse> findMerchantSummariesByOwner(long ownerUserId, Long restaurantId,
                                                                                       String status, int limit, long offset) {
                    if (ownerUserId != 10L) {
                        return List.of();
                    }
                    MerchantOrderSummaryResponse order = new MerchantOrderSummaryResponse(
                            101L,
                            "FD2611110101",
                            1L,
                            "Cơm Tấm Demo",
                            "PLACED",
                            BigDecimal.valueOf(65000),
                            2,
                            "Nguyễn Văn Khách",
                            "0911000001",
                            "10 Lê Lợi, Q1",
                            "Ít cơm, nhiều sườn",
                            "COD",
                            List.of(
                                    new OrderItemResponse(5L, "Cơm sườn nướng", BigDecimal.valueOf(25000), 2, "Không hành", BigDecimal.valueOf(50000))
                            ),
                            LocalDateTime.of(2026, 11, 11, 20, 0)
                    );
                    if (status != null && !status.equals(order.status())) {
                        return List.of();
                    }
                    return List.of(order);
                }

                @Override
                public long countByOwner(long ownerUserId, Long restaurantId, String status) {
                    if (ownerUserId != 10L) {
                        return 0;
                    }
                    if (status != null && !status.equals("PLACED")) {
                        return 0;
                    }
                    return 1L;
                }
            };
        }

        @Bean
        OrderService orderService(OrderRepository orderRepository) {
            return new OrderService(null, orderRepository);
        }
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/merchant/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessMerchantOrders() throws Exception {
        String token = jwtService.generateToken(20L, "CUSTOMER");
        mockMvc.perform(get("/api/merchant/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotAccessMerchantOrders() throws Exception {
        String token = jwtService.generateToken(30L, "ADMIN");
        mockMvc.perform(get("/api/merchant/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchantGetsOwnOrdersWithItemsNoteAndPhone() throws Exception {
        String token = jwtService.generateToken(10L, "MERCHANT");
        mockMvc.perform(get("/api/merchant/orders")
                        .param("restaurantId", "1")
                        .param("status", "PLACED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value("FD2611110101"))
                .andExpect(jsonPath("$.items[0].receiverName").value("Nguyễn Văn Khách"))
                .andExpect(jsonPath("$.items[0].receiverPhone").value("0911000001"))
                .andExpect(jsonPath("$.items[0].deliveryAddress").value("10 Lê Lợi, Q1"))
                .andExpect(jsonPath("$.items[0].note").value("Ít cơm, nhiều sườn"))
                .andExpect(jsonPath("$.items[0].totalAmount").value(65000))
                .andExpect(jsonPath("$.items[0].items[0].name").value("Cơm sườn nướng"))
                .andExpect(jsonPath("$.items[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].items[0].unitPrice").value(25000));
    }

    @Test
    void merchantAcceptsEmptyStatusAsAll() throws Exception {
        String token = jwtService.generateToken(10L, "MERCHANT");
        mockMvc.perform(get("/api/merchant/orders")
                        .param("restaurantId", "1")
                        .param("status", "")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void merchantCannotAccessAnotherOwnersRestaurantId() throws Exception {
        String token = jwtService.generateToken(10L, "MERCHANT");
        mockMvc.perform(get("/api/merchant/orders")
                        .param("restaurantId", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonExistentRestaurantIdReturns404() throws Exception {
        String token = jwtService.generateToken(10L, "MERCHANT");
        mockMvc.perform(get("/api/merchant/orders")
                        .param("restaurantId", "9999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        String token = jwtService.generateToken(10L, "MERCHANT");
        mockMvc.perform(get("/api/merchant/orders")
                        .param("status", "UNKNOWN_STATUS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}

