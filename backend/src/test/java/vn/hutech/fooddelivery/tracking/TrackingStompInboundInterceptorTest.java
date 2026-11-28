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
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
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
    private static final long MERCHANT = 55;
    private static final String TRACKING_TOPIC = "/topic/orders/73/location";
    private static final String MERCHANT_TOPIC = "/topic/restaurants/12/orders";
    private static final String NOTIFICATIONS = "/user/queue/notifications";

    @Mock UserRepository users;
    @Mock OrderTrackingRepository orders;
    @Mock OrderRepository merchantOrders;
    @Mock MessageChannel channel;

    private final String signingKey = UUID.randomUUID().toString();
    private final JwtService jwt = new JwtService(new JwtProperties(signingKey, 5));
    private TrackingStompInboundInterceptor interceptor;
    private final java.util.Map<Principal,String> sessionIds = new java.util.IdentityHashMap<>();

    @BeforeEach
    void setUp() {
        interceptor = new TrackingStompInboundInterceptor(jwt, users,
                new StompAuthorizationSessions(jwt, users, new OrderTrackingSubscriptionAuthorization(orders),
                        new MerchantOrderSubscriptionAuthorization(merchantOrders, users),
                        org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)));
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
    void invalidSignatureIsRejectedAtConnect() {
        var wrongJwt = new JwtService(new JwtProperties(UUID.randomUUID().toString(), 5));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader("authorization", "Bearer " + wrongJwt.generateToken(OWNER, "CUSTOMER"));

        assertDenied(connect);

        assertNull(connect.getNativeHeader("authorization"));
        verifyNoInteractions(users, orders);
    }

    @Test
    void expiredSessionIsRejectedAtConnect() {
        var expired = new JwtService(new JwtProperties(signingKey, -5));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expired.generateToken(MERCHANT, "MERCHANT"));
        assertDenied(connect);
        assertNull(connect.getUser());
        verifyNoInteractions(users, orders, merchantOrders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DELETED"})
    void blockedAccountCannotAuthenticate(String accountStatus) {
        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "CUSTOMER", accountStatus)));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(OWNER, "CUSTOMER"));

        assertDenied(connect);

        assertNull(connect.getUser());
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

    @Test
    void merchantCanSubscribeOnlyToRestaurantsOwnedByUserId() {
        Principal principal = connect(MERCHANT, "MERCHANT");
        when(merchantOrders.isRestaurantOwnedBy(12, MERCHANT)).thenReturn(true);
        when(merchantOrders.isRestaurantOwnedBy(13, MERCHANT)).thenReturn(true);
        assertAllowed(frame(StompCommand.SUBSCRIBE, MERCHANT_TOPIC, principal));
        assertAllowed(frame(StompCommand.SUBSCRIBE, "/topic/restaurants/13/orders", principal));
        assertDenied(frame(StompCommand.SUBSCRIBE, MERCHANT_TOPIC, connect(56, "MERCHANT")));
        assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/restaurants/99/orders", principal));
        verifyNoInteractions(orders);
    }

    @Test
    void guestCannotSubscribeToMerchantTopic() {
        assertDenied(frame(StompCommand.SUBSCRIBE, MERCHANT_TOPIC, null));
        verifyNoInteractions(users, orders, merchantOrders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "DRIVER", "ADMIN"})
    void merchantTopicRequiresMerchantRole(String role) {
        assertDenied(frame(StompCommand.SUBSCRIBE, MERCHANT_TOPIC, connect(MERCHANT, role)));
        verifyNoInteractions(merchantOrders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DELETED"})
    void existingMerchantMustStillBeActiveForNewSubscription(String status) {
        Principal principal = connect(MERCHANT, "MERCHANT");
        when(users.findById(MERCHANT)).thenReturn(Optional.of(user(MERCHANT, "MERCHANT", status)));
        assertDenied(frame(StompCommand.SUBSCRIBE, MERCHANT_TOPIC, principal));
        verifyNoInteractions(merchantOrders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/restaurants/*/orders", "/topic/restaurants/0/orders",
            "/topic/restaurants/-12/orders", "/topic/restaurants/012/orders",
            "/topic/restaurants/9223372036854775808/orders", "/topic/restaurants/12/orders/extra"})
    void merchantTopicRequiresCanonicalPositiveLong(String destination) {
        assertDenied(frame(StompCommand.SUBSCRIBE, destination, connect(MERCHANT, "MERCHANT")));
        verifyNoInteractions(merchantOrders);
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"SEND", "MESSAGE"})
    void broadcastDestinationsAreReadOnlyOnClientChannel(StompCommand command) {
        assertDenied(frame(command, MERCHANT_TOPIC, null));
        assertDenied(frame(command, "/topic/restaurants/12/orders/extra", null));
        assertDenied(frame(command, "/queue/notifications", null));
        assertDenied(frame(command, NOTIFICATIONS, null));
        assertAllowed(frame(command, "/app/example", null));
        verifyNoInteractions(merchantOrders, orders, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT"})
    void personalInboxUsesTheStompIdentityAndCurrentAccountRole(String role) {
        var authentication = assertInstanceOf(Authentication.class, connect(OWNER, role));
        assertEquals("user:" + OWNER, authentication.getName());
        assertEquals(new AuthUser(OWNER, role), authentication.getPrincipal());
        assertAllowed(frame(StompCommand.SUBSCRIBE, NOTIFICATIONS, authentication));

        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, role, "LOCKED")));
        assertDenied(frame(StompCommand.SUBSCRIBE, NOTIFICATIONS, authentication));
        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, role, "DELETED")));
        assertDenied(frame(StompCommand.SUBSCRIBE, NOTIFICATIONS, authentication));
        when(users.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "DRIVER", "ACTIVE")));
        assertDenied(frame(StompCommand.SUBSCRIBE, NOTIFICATIONS, authentication));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRIVER", "ADMIN"})
    void rolesOutsideWebCannotSubscribeToPersonalInbox(String role) {
        assertDenied(frame(StompCommand.SUBSCRIBE, NOTIFICATIONS, connect(OWNER, role)));
    }

    @Test
    void guestCannotSubscribeToPersonalInbox() {
        assertDenied(frame(StompCommand.SUBSCRIBE, NOTIFICATIONS, null));
        verifyNoInteractions(users, orders, merchantOrders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/queue/notifications", "/queue/notifications-userfixture",
            "/user/user:42/queue/notifications", "/user/queue/*", "/user/queue/notifications/extra"})
    void personalInboxAllowsOnlyItsCanonicalSubscription(String destination) {
        assertDenied(frame(StompCommand.SUBSCRIBE, destination, connect(OWNER, "CUSTOMER")));
        verifyNoInteractions(orders, merchantOrders);
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
        headers.setSessionId(sessionIds.get(user));
        return headers;
    }

    private void assertAllowed(StompHeaderAccessor headers) {
        Message<byte[]> message = message(headers);
        assertSame(message, interceptor.preSend(message, channel));
        if (headers.getUser() != null) sessionIds.put(headers.getUser(), headers.getSessionId());
    }

    private void assertDenied(StompHeaderAccessor headers) {
        Message<byte[]> message = message(headers);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    private Message<byte[]> message(StompHeaderAccessor headers) {
        if (headers.getSessionId() == null) headers.setSessionId(UUID.randomUUID().toString());
        if (headers.getCommand() == StompCommand.SUBSCRIBE && headers.getSubscriptionId() == null) {
            headers.setSubscriptionId(UUID.randomUUID().toString());
        }
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
