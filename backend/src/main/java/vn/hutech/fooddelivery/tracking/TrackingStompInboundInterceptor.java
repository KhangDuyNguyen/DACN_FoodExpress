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
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class TrackingStompInboundInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORDER_TOPIC = "/topic/orders";
    private static final Pattern RESTAURANT_ORDERS = Pattern.compile("/topic/restaurants/[1-9][0-9]*/orders");

    private final JwtService jwtService;
    private final UserRepository users;
    private final OrderTrackingSubscriptionAuthorization authorization;

    public TrackingStompInboundInterceptor(JwtService jwtService, UserRepository users,
                                           OrderTrackingSubscriptionAuthorization authorization) {
        this.jwtService = jwtService;
        this.users = users;
        this.authorization = authorization;
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
                if (isOrderTopic(headers.getDestination())) {
                    throw new AccessDeniedException("Client không được gửi dữ liệu vào topic theo dõi đơn");
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
        if (authorizationHeaders.size() != 1) {
            return;
        }
        String header = authorizationHeaders.getFirst();
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return;
        }

        long userId;
        try {
            userId = jwtService.parse(header.substring(BEARER_PREFIX.length())).id();
        } catch (JwtException | IllegalArgumentException ignored) {
            return;
        }
        users.findById(userId).filter(user -> !user.isBlocked()).ifPresent(user -> {
            var principal = new AuthUser(user.id(), user.role());
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
            headers.setUser(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        });
    }

    private void checkSubscription(StompHeaderAccessor headers) {
        String destination = headers.getDestination();
        // The merchant web app subscribes to a concrete topic for each restaurant.
        if (destination != null && RESTAURANT_ORDERS.matcher(destination).matches()) {
            return;
        }
        // Every other subscription must match one exact tracking topic. This rules out broker wildcards.
        if (headers.getUser() instanceof Authentication authentication
                && authorization.canSubscribe(authentication, destination)) {
            return;
        }
        throw new AccessDeniedException("Không có quyền đăng ký topic này");
    }

    private static boolean isOrderTopic(String destination) {
        return destination != null && (destination.equals(ORDER_TOPIC) || destination.startsWith(ORDER_TOPIC + "/"));
    }
}
