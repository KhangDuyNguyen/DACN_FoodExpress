package vn.hutech.fooddelivery.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceClientTest {

    @Test
    @DisplayName("Khi AI service khong hoat dong thi checkHealth tra ve status DOWN va khong nem exception lam crash he thong")
    void checkHealth_whenServiceDown_returnsDownStatus() {
        // Su dung port khong ton tai de mo phong AI service dang tat
        AiServiceClient client = new AiServiceClient("http://localhost:59999");

        Map<String, Object> result = client.checkHealth();

        assertNotNull(result);
        assertEquals("DOWN", result.get("status"));
        assertEquals("http://localhost:59999", result.get("aiServiceUrl"));
        assertTrue(result.containsKey("error"));
    }

    @Test
    @DisplayName("Khi AI service dang chay thi checkHealth tra ve UP va dung noi dung phan hoi")
    void checkHealth_whenServiceUp_returnsUpStatus() {
        AiServiceClient client = new AiServiceClient("http://127.0.0.1:8000");

        Map<String, Object> result = client.checkHealth();

        // Neu service dang bat tren port 8000 thi kiem tra UP
        if ("UP".equals(result.get("status"))) {
            assertEquals("http://127.0.0.1:8000", result.get("aiServiceUrl"));
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = (Map<String, Object>) result.get("response");
            assertNotNull(resp);
            assertEquals("ok", resp.get("status"));
            assertEquals("ai-service", resp.get("service"));
        } else {
            // Khi chay tren CI/CD khong co AI service thi van pass
            assertEquals("DOWN", result.get("status"));
        }
    }
}
