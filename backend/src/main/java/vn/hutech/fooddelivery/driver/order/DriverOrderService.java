package vn.hutech.fooddelivery.driver.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DriverOrderService {
    private final DriverOrderRepository orders;
    private final Clock clock;

    @Autowired
    public DriverOrderService(DriverOrderRepository orders) {
        this(orders, Clock.systemDefaultZone());
    }

    DriverOrderService(DriverOrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DriverCurrentOrderResponse getCurrentOrder(long userId) {
        long driverId = orders.findDriverId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        var current = orders.findCurrentOrders(driverId);
        if (current.size() > 1) {
            throw new ConflictException("Tài xế đang được gắn nhiều đơn hoạt động. Vui lòng liên hệ hỗ trợ");
        }
        // Loi truy van truyen ra ngoai; null chi dai dien cho ket qua doc thanh cong khong co don.
        return new DriverCurrentOrderResponse(current.isEmpty() ? null : current.getFirst(), LocalDateTime.now(clock));
    }
}
