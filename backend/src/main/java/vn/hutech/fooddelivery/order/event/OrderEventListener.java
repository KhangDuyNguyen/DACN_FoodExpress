package vn.hutech.fooddelivery.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lắng nghe sự kiện tạo đơn hàng sau khi transaction đã commit thành công
 * và broadcast thông tin đơn hàng mới qua WebSocket STOMP tới chủ quán.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public OrderEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String destination = "/topic/restaurants/" + event.restaurantId() + "/orders";
        log.info("Broadcasting OrderPlacedEvent for order #{} to topic {}", event.orderId(), destination);
        messagingTemplate.convertAndSend(destination, event.order());
    }
}
