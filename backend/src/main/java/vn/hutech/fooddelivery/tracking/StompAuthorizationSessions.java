package vn.hutech.fooddelivery.tracking;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.hutech.fooddelivery.notification.NotificationRealtimeChannel;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class StompAuthorizationSessions {
    static final int MAX_SESSIONS = 2048;
    static final int MAX_SUBSCRIPTIONS = 64;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Session> rotation = new ConcurrentLinkedQueue<>();
    private final JwtService jwt;
    private final UserRepository users;
    private final OrderTrackingSubscriptionAuthorization tracking;
    private final MerchantOrderSubscriptionAuthorization merchant;
    private final TransactionTemplate permissionRead;

    public StompAuthorizationSessions(JwtService jwt, UserRepository users,
            OrderTrackingSubscriptionAuthorization tracking, MerchantOrderSubscriptionAuthorization merchant,
            PlatformTransactionManager transactions) {
        this.jwt = jwt;
        this.users = users;
        this.tracking = tracking;
        this.merchant = merchant;
        permissionRead = new TransactionTemplate(transactions);
        permissionRead.setReadOnly(true);
        permissionRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        permissionRead.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        permissionRead.setTimeout(2);
    }

    synchronized void connected(String id, String bearer, Authentication authentication) {
        if (id == null || bearer == null || sessions.size() >= MAX_SESSIONS || sessions.containsKey(id)) {
            throw new org.springframework.security.access.AccessDeniedException("WebSocket session unavailable");
        }
        Session state = new Session(id, bearer, authentication);
        sessions.put(id, state);
        rotation.add(state);
    }

    Subscription subscribe(String id, String subscription, String destination) {
        Session state = sessions.get(id);
        if (state == null || subscription == null || subscription.length() > 128 || destination == null
                || destination.length() > 256 || !authorized(state, destination)) return null;
        synchronized (state) {
            if (state.revoked.get() || state.destinations.size() >= MAX_SUBSCRIPTIONS) return null;
            Subscription registration = new Subscription(state, subscription, destination);
            return state.destinations.putIfAbsent(subscription, registration) == null ? registration : null;
        }
    }

    boolean authorized(Session state, String destination) {
        if (state.revoked.get()) return false;
        try {
            return Boolean.TRUE.equals(permissionRead.execute(status -> {
                AuthUser token = jwt.parse(state.bearer);
                AuthUser identity = (AuthUser) state.authentication.getPrincipal();
                var account = users.findById(identity.id());
                if (token.id() != identity.id() || account.isEmpty() || account.get().isBlocked()
                        || !identity.role().equals(account.get().role())) return false;
                if (destination == null) return true;
                return tracking.canSubscribe(state.authentication, destination)
                        || merchant.canSubscribe(state.authentication, destination)
                        || (NotificationRealtimeChannel.SUBSCRIPTION.equals(destination)
                            && NotificationRealtimeChannel.supportsRole(identity.role()));
            })) && !state.revoked.get();
        } catch (RuntimeException exception) {
            // Missing permission data and unavailable storage both deny delivery.
            return false;
        }
    }

    Session get(String id) { return id == null ? null : sessions.get(id); }
    void unsubscribe(String id, String subscription) {
        Session state = get(id);
        if (state != null && subscription != null) state.destinations.remove(subscription);
    }
    synchronized void disconnected(String id) {
        disconnected(get(id));
    }
    synchronized void disconnected(Session state) {
        if (state != null && sessions.remove(state.id, state)) {
            state.revoked.set(true);
            state.destinations.clear();
            rotation.remove(state);
        }
    }
    void rollback(Subscription registration) {
        if (registration != null && sessions.get(registration.session.id) == registration.session) {
            registration.session.destinations.remove(registration.id, registration);
        }
    }

    static final class Subscription {
        final Session session;
        final String id;
        final String destination;
        Subscription(Session session, String id, String destination) {
            this.session = session;
            this.id = id;
            this.destination = destination;
        }
    }

    Session next() { return rotation.poll(); }
    void rotate(Session state) { if (sessions.get(state.id) == state) rotation.add(state); }
    public int size() { return sessions.size(); }
    public int subscriptionCount() { return sessions.values().stream().mapToInt(s -> s.destinations.size()).sum(); }

    static boolean protectedDestination(String destination) {
        return destination != null && (destination.startsWith("/topic/orders/")
                || destination.startsWith("/topic/restaurants/") || destination.startsWith("/queue/notifications")
                || destination.startsWith("/user/queue/notifications"));
    }

    static final class Session {
        final String id;
        final String bearer;
        final Authentication authentication;
        final Map<String, Subscription> destinations = new ConcurrentHashMap<>();
        final AtomicBoolean revoked = new AtomicBoolean();
        final AtomicBoolean closing = new AtomicBoolean();
        Session(String id, String bearer, Authentication authentication) {
            this.id = id;
            this.bearer = bearer;
            this.authentication = authentication;
        }
    }
}
