package vn.hutech.fooddelivery.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.hutech.fooddelivery.assignment.AssignmentOfferEvent;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.order.event.OrderStatusChangedEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import static vn.hutech.fooddelivery.notification.NotificationInboxService.*;

@Component
class NotificationBusinessEvents {
    private static final Logger log = LoggerFactory.getLogger(NotificationBusinessEvents.class);
    private final NotificationRecipientRepository recipients;
    private final OrderRepository orders;
    private final ApplicationEventPublisher publisher;
    private final CommittedNotificationWriter writer;
    private final NotificationRealtimePublisher realtime;
    private final NotificationFcmPublisher fcm;

    NotificationBusinessEvents(NotificationRecipientRepository recipients, OrderRepository orders,
                               ApplicationEventPublisher publisher, CommittedNotificationWriter writer,
                               NotificationRealtimePublisher realtime, NotificationFcmPublisher fcm) {
        this.recipients = recipients;
        this.orders = orders;
        this.publisher = publisher;
        this.writer = writer;
        this.realtime = realtime;
        this.fcm = fcm;
    }

    @EventListener
    public void orderPlaced(OrderPlacedEvent event) {
        capture(event.orderId(), () -> recipients.findOrder(event.orderId(), event.restaurantId())
                .filter(order -> "PLACED".equals(order.status()))
                .map(order -> {
                    var notices = new ArrayList<Draft>();
                    var message = new Message("order:" + event.orderId() + ":placed",
                            "ORDER_PLACED", new Data(event.orderId(), order.code(), null, null));
                    message.add(notices, order.customerUserId(), "Đặt hàng thành công", "Đơn hàng đã được đặt.");
                    message.add(notices, order.merchantUserId(), "Có đơn hàng mới", "Cửa hàng có một đơn hàng mới.");
                    return notices;
                }).orElseGet(ArrayList::new));
    }

    @EventListener
    public void assignmentOffer(AssignmentOfferEvent event) {
        capture(event.orderId(), () -> {
            var notices = new ArrayList<Draft>();
            recipients.findOrder(event.orderId(), event.restaurantId()).filter(order -> "PLACED".equals(order.status()))
                    .ifPresent(order -> {
                        boolean created = event.kind() == AssignmentOfferEvent.Kind.CREATED;
                        String key = "offer:" + event.offerId() + (created ? ":offered" : ":accepted");
                        String type = created ? "OFFER_CREATED" : "OFFER_ACCEPTED";
                        var message = new Message(key, type,
                                new Data(event.orderId(), order.code(), event.offerId(), event.expiresAt()));
                        if (created) {
                            message.add(notices, recipients.driverUserId(event.driverId()), "Bạn có yêu cầu nhận đơn mới",
                                    "Mở ứng dụng để xem lời mời giao hàng.");
                        } else if (Objects.equals(order.driverId(), event.driverId())) {
                            message.add(notices, order.merchantUserId(), "Tài xế đã nhận đơn",
                                    "Một tài xế đã nhận giao đơn hàng.");
                        }
                    });
            return notices;
        });
    }

    @EventListener
    public void orderStatusChanged(OrderStatusChangedEvent event) {
        String status = event.toStatus();
        if (Objects.equals(event.fromStatus(), status) || !List.of(
                "PREPARING", "READY_FOR_PICKUP", "DELIVERING", "DELIVERED", "CANCELLED").contains(status)) {
            return;
        }
        capture(event.orderId(), () -> {
            var notices = new ArrayList<Draft>();
            recipients.findOrder(event.orderId(), event.restaurantId())
                    .filter(order -> status.equals(order.status()) && order.orderCustomerId() == event.customerId())
                    .ifPresent(order -> addStatusNotices(notices, event, order));
            return notices;
        });
    }

    private void addStatusNotices(List<Draft> notices, OrderStatusChangedEvent event,
                                  NotificationRecipientRepository.OrderRecipients order) {
        String status = event.toStatus();
        var message = new Message("order:" + event.orderId() + ":" + status.toLowerCase(Locale.ROOT), "ORDER_" + status,
                new Data(event.orderId(), order.code(), null, null));
        switch (status) {
            case "PREPARING" -> message.add(notices, order.customerUserId(),
                    "Cửa hàng đã nhận đơn", "Cửa hàng đang chuẩn bị món.");
            case "READY_FOR_PICKUP" -> {
                message.add(notices, order.customerUserId(), "Món ăn đã sẵn sàng", "Món của bạn đã được chuẩn bị xong.");
                message.add(notices, order.driverUserId(), "Món ăn đã sẵn sàng", "Bạn có thể đến cửa hàng lấy món.");
            }
            case "DELIVERING" -> {
                message.add(notices, order.customerUserId(), "Đơn hàng đang đến", "Đơn hàng của bạn đang được giao.");
                message.add(notices, order.merchantUserId(), "Đơn hàng bắt đầu giao", order.driverUserId() == null
                        ? "Đơn hàng đang được cửa hàng giao." : "Tài xế đã lấy món và bắt đầu giao.");
            }
            case "DELIVERED" -> {
                message.add(notices, order.customerUserId(), "Giao hàng thành công", "Đơn hàng của bạn đã được giao.");
                message.add(notices, order.merchantUserId(), "Giao hàng thành công", "Đơn hàng đã được giao cho khách.");
                message.add(notices, order.driverUserId(), "Hoàn thành chuyến giao", "Bạn đã hoàn thành chuyến giao.");
            }
            case "CANCELLED" -> addCancellation(notices, event, order, message);
            default -> throw new IllegalStateException("Unsupported notification status");
        }
    }

    private void addCancellation(List<Draft> notices, OrderStatusChangedEvent event,
                                 NotificationRecipientRepository.OrderRecipients order, Message message) {
        if ("PENDING_PAYMENT".equals(event.fromStatus())) {
            message.add(notices, order.customerUserId(), "Thanh toán không thành công", "Đơn hàng chưa thanh toán đã bị hủy.");
            return;
        }
        if (event.changedBy() == order.orderCustomerId()) {
            if (orders.isVisibleToMerchant(event.orderId())) {
                message.add(notices, order.merchantUserId(), "Khách hàng đã hủy đơn", "Khách hàng đã hủy đơn hàng.");
            }
        } else if (Objects.equals(order.merchantUserId(), event.changedBy())) {
            message.add(notices, order.customerUserId(), "Đơn hàng bị hủy", "Cửa hàng đã hủy đơn hàng.");
        } else {
            return;
        }
        // Current order rules forbid cancellation with an assigned driver. The order lock protects the offer check.
        recipients.effectiveOfferDrivers(event.orderId(), LocalDateTime.now()).stream()
                .filter(driver -> driver != event.changedBy())
                .forEach(driver -> message.add(notices, driver, "Lời mời giao hàng bị hủy", "Đơn hàng không còn hiệu lực."));
    }

    private void capture(long orderId, Supplier<List<Draft>> projection) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        try {
            var drafts = projection.get();
            if (!drafts.isEmpty()) {
                publisher.publishEvent(new Snapshot(orderId, List.copyOf(drafts)));
            }
        } catch (RuntimeException ex) {
            log.warn("Could not capture notification recipients for order {} ({})", orderId, ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void saveCommitted(Snapshot snapshot) {
        List<CommittedNotificationWriter.SavedNotification> saved;
        try {
            saved = writer.save(snapshot.drafts());
        } catch (RuntimeException ex) {
            log.warn("Could not write committed notifications for order {} ({})", snapshot.orderId(),
                    ex.getClass().getSimpleName());
            return;
        }
        saved.forEach(realtime::publish);
        saved.forEach(fcm::enqueue);
    }

    record Snapshot(long orderId, List<Draft> drafts) {}

    private record Message(String key, String type, Data data) {
        void add(List<Draft> notices, Long recipient, String title, String body) {
            if (recipient != null) {
                notices.add(new Draft(recipient, Type.ORDER, key, type, title, body, data));
            }
        }
    }
}
