package vn.hutech.fooddelivery.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderTrackingLocationSavedListener {
    private static final Logger log = LoggerFactory.getLogger(OrderTrackingLocationSavedListener.class);

    private final OrderTrackingLocationPublisher publisher;

    public OrderTrackingLocationSavedListener(OrderTrackingLocationPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocationSaved(OrderTrackingLocationSavedEvent event) {
        try {
            publisher.publish(event.orderId(),
                    new OrderTrackingResponse.Location(event.lat(), event.lng(), event.recordedAt()));
        } catch (RuntimeException failure) {
            log.warn("Không thể phát vị trí sau commit cho đơn {} ({})",
                    event.orderId(), failure.getClass().getSimpleName());
        }
    }
}
