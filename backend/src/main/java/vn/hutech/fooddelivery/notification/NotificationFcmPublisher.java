package vn.hutech.fooddelivery.notification;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
class NotificationFcmPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationFcmPublisher.class);
    private static final Set<String> CUSTOMER_EVENTS = Set.of("ORDER_PLACED", "ORDER_PREPARING",
            "ORDER_READY_FOR_PICKUP", "ORDER_DELIVERING", "ORDER_DELIVERED", "ORDER_CANCELLED");
    private static final Set<String> DRIVER_EVENTS =
            Set.of("OFFER_CREATED", "ORDER_READY_FOR_PICKUP", "ORDER_DELIVERED", "ORDER_CANCELLED");

    private final DeviceTokenRepository tokens;
    private final NotificationFcmDeliveryRepository deliveries;
    private final FirebaseCustomerPush gateway;
    private final FcmPushSource source;
    private final Clock clock;
    private final ExecutorService executor;

    @Autowired
    NotificationFcmPublisher(DeviceTokenRepository tokens, NotificationFcmDeliveryRepository deliveries,
                             FirebaseCustomerPush gateway, @Value("${app.fcm.source:LEGACY}") FcmPushSource source) {
        this(tokens, deliveries, gateway, source, Clock.systemDefaultZone(),
                new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128),
                        Thread.ofPlatform().daemon().name("notification-fcm").factory()));
    }

    NotificationFcmPublisher(DeviceTokenRepository tokens, NotificationFcmDeliveryRepository deliveries,
                             FirebaseCustomerPush gateway, FcmPushSource source, Clock clock, ExecutorService executor) {
        this.tokens = tokens;
        this.deliveries = deliveries;
        this.gateway = gateway;
        this.source = source;
        this.clock = clock;
        this.executor = executor;
    }

    void enqueue(CommittedNotificationWriter.SavedNotification saved) {
        try {
            if (source == FcmPushSource.INBOX && gateway.enabled()) {
                executor.execute(() -> send(saved));
            }
        } catch (RuntimeException ex) {
            log.warn("Could not queue notification {} for FCM ({})", saved.notificationId(),
                    ex.getClass().getSimpleName());
        }
    }

    private void send(CommittedNotificationWriter.SavedNotification saved) {
        try {
            for (String token : tokens.findActiveAndroidTokens(saved.userId())) {
                sendToDevice(saved, token);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not list devices for notification {} ({})", saved.notificationId(),
                    ex.getClass().getSimpleName());
        }
    }

    private void sendToDevice(CommittedNotificationWriter.SavedNotification saved, String token) {
        try {
            var result = deliveries.find(saved, token, LocalDateTime.now(clock));
            if (result.isEmpty() || !allowed(result.get().role(), saved.eventType())) {
                return;
            }
            Long ttl = null;
            if ("OFFER_CREATED".equals(saved.eventType())) {
                LocalDateTime expiry = result.get().offerExpiry();
                if (expiry == null) return;
                ttl = Duration.between(LocalDateTime.now(clock), expiry).toMillis();
                if (ttl <= 0) return;
            }
            if (!gateway.sendInbox(token, saved.notificationId(), saved.eventType(), ttl)) {
                tokens.remove(saved.userId(), token);
            }
        } catch (Exception ex) {
            log.warn("Could not send FCM notification {} ({})", saved.notificationId(), ex.getClass().getSimpleName());
        }
    }

    private static boolean allowed(String role, String eventType) {
        return switch (role) {
            case "CUSTOMER" -> CUSTOMER_EVENTS.contains(eventType);
            case "DRIVER" -> DRIVER_EVENTS.contains(eventType);
            default -> false;
        };
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
