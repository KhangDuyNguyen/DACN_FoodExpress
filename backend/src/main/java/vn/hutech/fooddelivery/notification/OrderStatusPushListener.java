package vn.hutech.fooddelivery.notification;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.hutech.fooddelivery.order.event.OrderStatusChangedEvent;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
class OrderStatusPushListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderStatusPushListener.class);

    private final DeviceTokenRepository tokens;
    private final FirebaseCustomerPush push;
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), Thread.ofPlatform().daemon().name("order-push").factory());

    OrderStatusPushListener(DeviceTokenRepository tokens, FirebaseCustomerPush push) {
        this.tokens = tokens;
        this.push = push;
    }

    @PreDestroy
    void stopSending() {
        executor.shutdownNow();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderStatusChangedEvent event) {
        if (!push.enabled() || Objects.equals(event.fromStatus(), event.toStatus())) return;
        try {
            executor.execute(() -> sendToOwner(event.customerId()));
        } catch (RuntimeException e) {
            LOG.warn("Could not queue a committed order update for push");
        }
    }

    private void sendToOwner(long customerId) {
        try {
            for (String token : tokens.findActiveCustomerTokens(customerId)) {
                try {
                    if (!push.sendOrderUpdate(token)) tokens.remove(customerId, token);
                } catch (Exception e) {
                    LOG.warn("Order push could not be sent ({})", e.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not find recipients for a committed order update");
        }
    }
}
