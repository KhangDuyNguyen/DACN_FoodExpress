package vn.hutech.fooddelivery.tracking;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
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
public class TrackingStompInboundInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BROKER_PREFIX = "/topic";

    private final JwtService jwtService;
    private final UserRepository users;
    private final OrderTrackingSubscriptionAuthorization authorization;
    private final MerchantOrderSubscriptionAuthorization merchantAuthorization;

    public TrackingStompInboundInterceptor(JwtService jwtService, UserRepository users,
                                           OrderTrackingSubscriptionAuthorization authorization,
                                           MerchantOrderSubscriptionAuthorization merchantAuthorization) {
        this.jwtService = jwtService;
        this.users = users;
        this.authorization = authorization;
        this.merchantAuthorization = merchantAuthorization;
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
        var user = users.findById(userId).filter(account -> !account.isBlocked())
                .orElseThrow(TrackingStompInboundInterceptor::invalidSession);
        headers.setUser(new StompAuthentication(new AuthUser(user.id(), user.role())));
    }

    private void checkSubscription(StompHeaderAccessor headers) {
        String destination = headers.getDestination();
        if (headers.getUser() instanceof Authentication authentication
                && (authorization.canSubscribe(authentication, destination)
                    || merchantAuthorization.canSubscribe(authentication, destination)
                    || canSubscribeToNotifications(authentication, destination))) {
            return;
        }
        throw new AccessDeniedException("Không có quyền đăng ký topic này");
    }

    private boolean canSubscribeToNotifications(Authentication authentication, String destination) {
        if (!NotificationRealtimeChannel.SUBSCRIPTION.equals(destination)
                || !(authentication instanceof StompAuthentication session) || !session.isAuthenticated()) {
            return false;
        }
        AuthUser principal = (AuthUser) session.getPrincipal();
        return NotificationRealtimeChannel.supportsRole(principal.role())
                && users.findById(principal.id()).filter(account -> !account.isBlocked()
                        && principal.role().equals(account.role())).isPresent();
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
