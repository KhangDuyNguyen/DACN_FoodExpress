package vn.hutech.fooddelivery.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
        // User mau trong V2 (0901234567) co password_hash = '$2a$10$dummyhash', khong phai hash BCrypt hop le
        String body = """
                {"identifier":"0901234567","password":"matkhau123"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tài khoản hoặc mật khẩu"));
    }
}
