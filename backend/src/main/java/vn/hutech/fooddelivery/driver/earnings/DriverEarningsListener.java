package vn.hutech.fooddelivery.driver.earnings;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.order.event.OrderStatusChangedEvent;

@Component
public class DriverEarningsListener {
    private final OrderRepository orders;
    private final DriverEarningsRepository earnings;

    public DriverEarningsListener(OrderRepository orders, DriverEarningsRepository earnings) {
        this.orders = orders;
        this.earnings = earnings;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (!"DELIVERED".equals(event.toStatus()) || "DELIVERED".equals(event.fromStatus())) {
            return;
        }
        // OrderManagementService already holds this lock in the publishing transaction.
        var order = orders.lockForUpdate(event.orderId()).orElseThrow();
        if (order.driverId() != null) {
            earnings.insert(order.id(), order.driverId(), DriverEarningsCalculator.baseEarning(order.deliveryFee()));
        }
    }
}
