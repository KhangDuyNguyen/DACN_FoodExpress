package vn.hutech.fooddelivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import vn.hutech.fooddelivery.notification.NotificationRealtimeChannel;
import vn.hutech.fooddelivery.tracking.TrackingStompInboundInterceptor;
import vn.hutech.fooddelivery.tracking.TrackingStompOutboundInterceptor;
import vn.hutech.fooddelivery.tracking.StompAuthorizationLifecycle;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

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

    private final TrackingStompInboundInterceptor trackingInterceptor;
    private final TrackingStompOutboundInterceptor outbound;
    private final StompAuthorizationLifecycle lifecycle;

    public WebSocketConfig(TrackingStompInboundInterceptor trackingInterceptor,
            TrackingStompOutboundInterceptor outbound, StompAuthorizationLifecycle lifecycle) {
        this.outbound = outbound;
        this.lifecycle = lifecycle;
        this.trackingInterceptor = trackingInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(trackingInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(outbound);
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(128);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(lifecycle::decorate);
        registration.setSendTimeLimit(10_000).setSendBufferSizeLimit(128 * 1024);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topic dành cho các broadcast tới client (ví dụ /topic/restaurants/{id}/orders)
        registry.enableSimpleBroker("/topic", NotificationRealtimeChannel.QUEUE_PREFIX);
        registry.setUserDestinationPrefix(NotificationRealtimeChannel.USER_PREFIX);
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
