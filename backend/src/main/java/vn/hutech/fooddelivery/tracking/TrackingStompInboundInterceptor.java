package vn.hutech.fooddelivery.tracking;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import vn.hutech.fooddelivery.notification.NotificationRealtimeChannel;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Component
public class TrackingStompInboundInterceptor implements org.springframework.messaging.support.ExecutorChannelInterceptor {
    private static final String REGISTRATION_HEADER = "stompAuthorizationRegistration";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BROKER_PREFIX = "/topic";

    private final StompAuthorizationSessions sessions;
    private final JwtService jwtService;
    private final UserRepository users;

    public TrackingStompInboundInterceptor(JwtService jwtService, UserRepository users, StompAuthorizationSessions sessions) {
        this.jwtService = jwtService;
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message;
        }

        switch (headers.getCommand()) {
            case CONNECT, STOMP -> authenticateConnect(headers);
            case SUBSCRIBE -> checkSubscription(headers);
            case UNSUBSCRIBE -> sessions.unsubscribe(headers.getSessionId(), headers.getSubscriptionId());
            case DISCONNECT -> sessions.disconnected(headers.getSessionId());
            case SEND, MESSAGE -> {
                String destination = headers.getDestination();
                // Match the simple broker prefix. Backend publications do not use the client inbound channel.
                if (destination != null && (destination.startsWith(BROKER_PREFIX)
                        || destination.startsWith(NotificationRealtimeChannel.QUEUE_PREFIX)
                        || destination.startsWith(NotificationRealtimeChannel.USER_PREFIX))) {
                    throw new AccessDeniedException("Client không được gửi dữ liệu vào topic broadcast");
                }
            }
            default -> { }
        }
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception exception) {
        if (!sent || exception != null) rollbackRegistration(message);
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel,
            org.springframework.messaging.MessageHandler handler, Exception exception) {
        if (exception != null) rollbackRegistration(message);
    }

    private void rollbackRegistration(Message<?> message) {
        Object registration = message.getHeaders().get(REGISTRATION_HEADER);
        if (registration instanceof StompAuthorizationSessions.Subscription owned) sessions.rollback(owned);
    }

    private void authenticateConnect(StompHeaderAccessor headers) {
        var authorizationHeaders = new ArrayList<String>();
        for (String name : headers.toNativeHeaderMap().keySet()) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                authorizationHeaders.addAll(headers.removeNativeHeader(name));
            }
        }
        if (authorizationHeaders.isEmpty()) {
            return;
        }
        if (authorizationHeaders.size() != 1) {
            throw invalidSession();
        }
        String header = authorizationHeaders.getFirst();
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw invalidSession();
        }

        long userId;
        try {
            userId = jwtService.parse(header.substring(BEARER_PREFIX.length())).id();
        } catch (JwtException | IllegalArgumentException ignored) {
            throw invalidSession();
        }
        final vn.hutech.fooddelivery.user.UserRow user;
        try {
            user = users.findById(userId).filter(account -> !account.isBlocked())
                .orElseThrow(TrackingStompInboundInterceptor::invalidSession);
        } catch (RuntimeException exception) {
            throw invalidSession();
        }
        var authentication = new StompAuthentication(new AuthUser(user.id(), user.role()));
        sessions.connected(headers.getSessionId(), header.substring(BEARER_PREFIX.length()), authentication);
        headers.setUser(authentication);
    }

    private void checkSubscription(StompHeaderAccessor headers) {
        headers.setHeader(REGISTRATION_HEADER, null);
        var registration = sessions.subscribe(headers.getSessionId(), headers.getSubscriptionId(), headers.getDestination());
        if (registration == null) {
            throw new AccessDeniedException("Không có quyền đăng ký topic này");
        }
        // Server-local bookkeeping. This is not a STOMP native header or an outbound payload field.
        headers.setHeader(REGISTRATION_HEADER, registration);
    }

    private static final class StompAuthentication extends UsernamePasswordAuthenticationToken {
        private StompAuthentication(AuthUser user) {
            super(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        }

        @Override
        public String getName() {
            return NotificationRealtimeChannel.principalName(((AuthUser) getPrincipal()).id());
        }
    }

    private static AccessDeniedException invalidSession() {
        return new AccessDeniedException("Phiên WebSocket không hợp lệ");
    }
}
