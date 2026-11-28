package vn.hutech.fooddelivery.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
class CommittedNotificationWriter {
    private final NotificationInboxService inbox;

    CommittedNotificationWriter(NotificationInboxService inbox) {
        this.inbox = inbox;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SavedNotification> save(List<NotificationInboxService.Draft> drafts) {
        var saved = new ArrayList<SavedNotification>();
        for (var draft : drafts) {
            var recorded = inbox.record(draft);
            if (recorded.created()) {
                saved.add(new SavedNotification(draft.userId(), recorded.id(), draft.eventType()));
            }
        }
        return List.copyOf(saved);
    }

    record SavedNotification(long userId, long notificationId, String eventType) {}
}
