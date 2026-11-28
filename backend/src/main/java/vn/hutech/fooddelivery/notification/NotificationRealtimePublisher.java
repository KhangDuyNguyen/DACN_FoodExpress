package vn.hutech.fooddelivery.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import vn.hutech.fooddelivery.user.UserRepository;

@Component
class NotificationRealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationRealtimePublisher.class);
    private final SimpMessagingTemplate messaging;
    private final UserRepository users;

    NotificationRealtimePublisher(SimpMessagingTemplate messaging, UserRepository users) {
        this.messaging = messaging;
        this.users = users;
    }

    /** Invoked only once the writer's transaction proxy has committed the inbox batch. */
    void publish(CommittedNotificationWriter.SavedNotification saved) {
        try {
            users.findById(saved.userId())
                    .filter(user -> !user.isBlocked() && NotificationRealtimeChannel.supportsRole(user.role()))
                    .ifPresent(user -> messaging.convertAndSendToUser(
                            NotificationRealtimeChannel.principalName(user.id()), NotificationRealtimeChannel.QUEUE,
                            new Signal(saved.notificationId(), saved.eventType())));
        } catch (RuntimeException ex) {
            log.warn("Could not send realtime notification {} ({})", saved.notificationId(),
                    ex.getClass().getSimpleName());
        }
    }

    record Signal(long notificationId, String eventType) {}
}
