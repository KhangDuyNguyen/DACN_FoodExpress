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
import vn.hutech.fooddelivery.assignment.AssignmentService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bo lang nghe OrderAssignmentListener: phan gan tai xe (cua An) bi loi thi dat hang VAN thanh cong,
 * vi don da commit truoc khi goi gan tai xe. AssignmentService duoc thay bang mock luon nem loi.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=order_event_test_secret_dai_hon_32_ky_tu_abcd",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class OrderPlacedEventTest {

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
    AssignmentService assignmentService;

    @Test
    void orderSucceedsEvenWhenDriverAssignmentFails() throws Exception {
        when(assignmentService.createOffer(anyLong())).thenThrow(new IllegalStateException("gia lap loi gan tai xe"));

        String token = "Bearer " + objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"0911000001\",\"password\":\"Demo@12345\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
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

        String response = mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName":"Khách Test","receiverPhone":"0911000001",
                                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                                 "paymentMethod":"COD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).get("id").asLong();

        // Da goi moi tai xe dung 1 lan voi don vua tao, loi bi nuot (chi ghi log), don van con
        verify(assignmentService).createOffer(orderId);
        assertEquals("PLACED", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = :id", Map.of("id", orderId), String.class));
    }
}
