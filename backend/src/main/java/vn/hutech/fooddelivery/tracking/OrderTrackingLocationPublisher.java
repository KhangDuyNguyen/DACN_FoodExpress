package vn.hutech.fooddelivery.tracking;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
public class OrderTrackingLocationPublisher {
    private final SimpMessagingTemplate messaging;

    public OrderTrackingLocationPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publish(Long orderId, OrderTrackingResponse.Location location) {
        if (orderId == null || orderId <= 0 || location == null || location.recordedAt() == null
                || !validCoordinate(location.lat(), 90) || !validCoordinate(location.lng(), 180)) {
            return;
        }

        // Keep the REST ISO local date-time representation regardless of broker converter settings.
        String recordedAt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(location.recordedAt());
        messaging.convertAndSend("/topic/orders/" + orderId + "/location",
                new LocationMessage(location.lat(), location.lng(), recordedAt));
    }

    private static boolean validCoordinate(BigDecimal value, int limit) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) <= 0;
    }

    record LocationMessage(BigDecimal lat, BigDecimal lng, String recordedAt) {
    }
}
