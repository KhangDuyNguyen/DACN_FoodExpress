package vn.hutech.fooddelivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket STOMP cho hệ thống thông báo đơn hàng theo thời gian thực (Realtime).
 * Broker destination prefix: /topic
 * Endpoint: /ws (hỗ trợ cả native WebSocket cho @stomp/stompjs và SockJS fallback)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topic dành cho các broadcast tới client (ví dụ /topic/restaurants/{id}/orders)
        registry.enableSimpleBroker("/topic");
        // Prefix dành cho các message từ client gửi lên @MessageMapping (nếu có)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = (allowedOrigins != null && allowedOrigins.length > 0)
                ? allowedOrigins
                : new String[]{"*"};

        // Endpoint WebSocket trực tiếp (phục vụ STOMP client: ws://localhost:8080/ws)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins);

        // Endpoint WebSocket kèm SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }
}
