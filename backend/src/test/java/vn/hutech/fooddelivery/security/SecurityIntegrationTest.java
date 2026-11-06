package vn.hutech.fooddelivery.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tich hop: chay backend that voi MySQL 8.4 trong Docker (Testcontainers), Flyway tu tao bang + du lieu mau.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=integration_test_secret_dai_hon_32_ky_tu_abcdef",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    void meWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void customerTokenCanCallMeButNotAdmin() throws Exception {
        String body = """
                {"fullName":"Khach Test","phone":"0911111111","email":"khach@test.com",
                 "password":"matkhau123","role":"CUSTOMER"}
                """;
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String token = json.get("token").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0911111111"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/admin/x").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void loginWithInvalidBcryptHashInSeedDataReturns401NotServerError() throws Exception {
        // Seed V20261107_01 da doi hash cua user 0901234567 thanh hash that, nen tu tao 1 user co hash gia
        // ('$2a$10$dummyhash' khong phai BCrypt hop le) de kiem tra van tra 401 chu khong 500
        jdbc.update("""
                INSERT INTO users (full_name, phone, password_hash, role)
                VALUES ('Hash Gia', '0911999999', '$2a$10$dummyhash', 'CUSTOMER')
                """, Map.of());
        String body = """
                {"identifier":"0911999999","password":"matkhau123"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tài khoản hoặc mật khẩu"));
    }

    // ----- Tuan 3: tai khoan mau (V20261026_01), mat khau chung Demo@12345 -----

    private String loginAndGetToken(String phone) throws Exception {
        String body = """
                {"identifier":"%s","password":"Demo@12345"}
                """.formatted(phone);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void seededDemoAccountsCanLoginWithCorrectRole() throws Exception {
        String[][] accounts = {
                {"0911000001", "CUSTOMER"}, {"0922000001", "MERCHANT"}, {"0901234567", "MERCHANT"},
                {"0933000001", "DRIVER"}, {"0900000001", "ADMIN"}
        };
        for (String[] account : accounts) {
            String token = loginAndGetToken(account[0]);
            mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(account[1]));
        }
    }

    @Test
    void lockedAccountWithCorrectPasswordReturns403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"0911000009\",\"password\":\"Demo@12345\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị khóa"));
    }

    @Test
    void cartAndOrdersAreOnlyForCustomer() throws Exception {
        String merchantToken = loginAndGetToken("0922000001");
        String driverToken = loginAndGetToken("0933000001");

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCallMerchantOrDriverApi() throws Exception {
        String token = loginAndGetToken("0911000001");
        mockMvc.perform(get("/api/merchant/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/driver/x").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ----- Tuan 3: loi luon tra ve dang ApiError -----

    @Test
    void malformedJsonReturns400ApiError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{khong phai json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không đúng định dạng"));
    }

    @Test
    void wrongParameterTypeReturns400ApiError() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("cuisineId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownPathReturns404AndWrongMethodReturns405() throws Exception {
        String token = loginAndGetToken("0911000001");
        mockMvc.perform(get("/api/khong-ton-tai").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
