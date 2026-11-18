package vn.hutech.fooddelivery.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.promotion.DiscountType;
import vn.hutech.fooddelivery.promotion.VoucherService;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateResponse;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Voucher het luot DUNG LUC ghi luot dung (don da insert xong): ca don phai rollback,
 * vi tao don va ghi luot voucher nam chung 1 transaction.
 * Gia lap bang mock VoucherService: kiem tra ma bao hop le, nhung ghi luot dung nem 409.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=order_voucher_rollback_secret_dai_hon_32_ky_tu",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class OrderVoucherRollbackTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    VoucherService voucherService;

    @Test
    void orderIsRolledBackWhenVoucherRunsOutWhileSaving() throws Exception {
        when(voucherService.validateVoucher(any(), anyLong())).thenReturn(new VoucherValidateResponse(
                true, "Áp dụng mã giảm giá thành công", 999L, "HETLUOT", "Voucher giả lập", DiscountType.FIXED,
                new BigDecimal("10000"), null, null, null, null));
        doThrow(new ConflictException("Voucher đã hết lượt sử dụng hoặc không còn hiệu lực"))
                .when(voucherService).recordVoucherUsage(anyLong(), anyLong(), anyLong());

        String token = "Bearer " + objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"0911000002\",\"password\":\"Demo@12345\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        long customerId = jdbc.queryForObject("SELECT id FROM users WHERE phone = '0911000002'", Map.of(), Long.class);
        long menuItemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = 'Cơm Nhà Demo - Chi nhánh Quận 1' AND mi.name = 'Cơm sườn nướng'
                """, Map.of(), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", token)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":1}".formatted(menuItemId)))
                .andExpect(status().isOk());
        long ordersBefore = countOrders(customerId);

        mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName":"Khách Test","receiverPhone":"0911000002",
                                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                                 "paymentMethod":"COD","voucherCode":"HETLUOT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Voucher đã hết lượt sử dụng hoặc không còn hiệu lực"));

        // Da toi buoc ghi luot dung (tuc la don da insert), nhung loi -> don bien mat, gio con nguyen
        verify(voucherService).recordVoucherUsage(anyLong(), anyLong(), anyLong());
        assertEquals(ordersBefore, countOrders(customerId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = :id
                """, Map.of("id", customerId), Integer.class));
    }

    private long countOrders(long customerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id = :id", Map.of("id", customerId),
                Long.class);
    }
}
