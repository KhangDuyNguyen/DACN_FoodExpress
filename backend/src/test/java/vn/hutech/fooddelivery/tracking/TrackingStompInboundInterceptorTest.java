package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingStompInboundInterceptorTest {
    private static final long OWNER = 41;
    private static final long OTHER_CUSTOMER = 42;
    private static final long ORDER_ID = 73;
    private static final String TRACKING_TOPIC = "/topic/orders/73/location";
    private static final String MERCHANT_TOPIC = "/topic/restaurants/12/orders";

    @Mock UserRepository users;
    @Mock OrderTrackingRepository orders;
    @Mock MessageChannel channel;

    private final JwtService jwt = new JwtService(new JwtProperties("tracking-stomp-test-signing-secret-2026", 5));
    private TrackingStompInboundInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TrackingStompInboundInterceptor(jwt, users, new OrderTrackingSubscriptionAuthorization(orders));
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"CONNECT", "STOMP"})
    void ownerCanSubscribeWithIdentityResolvedFromDatabase(StompCommand command) {
        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "CUSTOMER", "ACTIVE")));
        when(orders.isOwnedBy(ORDER_ID, OWNER)).thenReturn(true);
        var sessionUser = new AtomicReference<Principal>();
        var connect = StompHeaderAccessor.create(command);
        connect.setUserChangeCallback(sessionUser::set);
        connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(OWNER, "DRIVER"));
        connect.addNativeHeader("customerId", Long.toString(OTHER_CUSTOMER));
        connect.addNativeHeader("role", "ADMIN");

        assertAllowed(connect);

        var authentication = assertInstanceOf(Authentication.class, sessionUser.get());
        assertEquals(new AuthUser(OWNER, "CUSTOMER"), authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertNull(connect.getNativeHeader(HttpHeaders.AUTHORIZATION));
        assertAllowed(frame(StompCommand.SUBSCRIBE, TRACKING_TOPIC, sessionUser.get()));
    }

    @Test
    void anotherCustomerCannotClaimOwnershipThroughSubscriptionHeaders() {
        var subscribe = frame(StompCommand.SUBSCRIBE, TRACKING_TOPIC, connect(OTHER_CUSTOMER, "CUSTOMER"));
        subscribe.addNativeHeader("customerId", Long.toString(OWNER));

        assertDenied(subscribe);

        verify(orders).isOwnedBy(ORDER_ID, OTHER_CUSTOMER);
    }

    @Test
    void jwtClaimCannotGrantCustomerRole() {
        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "DRIVER", "ACTIVE")));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(OWNER, "CUSTOMER"));
        assertAllowed(connect);

        assertDenied(frame(StompCommand.SUBSCRIBE, TRACKING_TOPIC, connect.getUser()));
        verifyNoInteractions(orders);
    }

    @Test
    void unauthenticatedSessionCannotAuthenticateOnSubscribe() {
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertAllowed(connect);
        var subscribe = frame(StompCommand.SUBSCRIBE, TRACKING_TOPIC, connect.getUser());
        subscribe.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(OWNER, "CUSTOMER"));

        assertDenied(subscribe);
        verifyNoInteractions(users, orders);
    }

    @Test
    void invalidSignatureDoesNotAuthenticateSession() {
        var wrongJwt = new JwtService(new JwtProperties("a-different-tracking-stomp-signing-key", 5));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader("authorization", "Bearer " + wrongJwt.generateToken(OWNER, "CUSTOMER"));

        assertAllowed(connect);

        assertNull(connect.getNativeHeader("authorization"));
        assertDenied(frame(StompCommand.SUBSCRIBE, TRACKING_TOPIC, connect.getUser()));
        verifyNoInteractions(users, orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DELETED"})
    void blockedAccountCannotAuthenticate(String accountStatus) {
        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "CUSTOMER", accountStatus)));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(OWNER, "CUSTOMER"));

        assertAllowed(connect);

        assertNull(connect.getUser());
        assertDenied(frame(StompCommand.SUBSCRIBE, TRACKING_TOPIC, connect.getUser()));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/**", "/topic/orders/**", "/topic/orders/*/location", "/topic/{family}/73/location",
            "/topic/restaurants/*/orders", "/topic/orders/73/location/extra"})
    void broadOrMalformedSubscriptionIsRejected(String destination) {
        assertDenied(frame(StompCommand.SUBSCRIBE, destination, connect(OWNER, "CUSTOMER")));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"SEND", "MESSAGE"})
    void evenOrderOwnerCannotPublishLocation(StompCommand command) {
        assertDenied(frame(command, TRACKING_TOPIC, connect(OWNER, "CUSTOMER")));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void merchantCanSubscribeToConcreteRestaurantTopicWithOrWithoutJwt(boolean withJwt) {
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (withJwt) {
            long merchantId = 12;
            when(users.findById(merchantId)).thenReturn(Optional.of(user(merchantId, "MERCHANT", "ACTIVE")));
            connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(merchantId, "MERCHANT"));
        }

        assertAllowed(connect);
        assertAllowed(frame(StompCommand.SUBSCRIBE, MERCHANT_TOPIC, connect.getUser()));
        verifyNoInteractions(orders);
    }

    private Principal connect(long userId, String role) {
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, role, "ACTIVE")));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(userId, role));
        assertAllowed(connect);
        return connect.getUser();
    }

    private UserRow user(long userId, String role, String status) {
        return new UserRow(userId, "STOMP Test", null, "0900000000", "unused", role, status,
                null, null, LocalDateTime.now());
    }

    private StompHeaderAccessor frame(StompCommand command, String destination, Principal user) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(user);
        return headers;
    }

    private void assertAllowed(StompHeaderAccessor headers) {
        Message<byte[]> message = message(headers);
        assertSame(message, interceptor.preSend(message, channel));
    }

    private void assertDenied(StompHeaderAccessor headers) {
        Message<byte[]> message = message(headers);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    private Message<byte[]> message(StompHeaderAccessor headers) {
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
