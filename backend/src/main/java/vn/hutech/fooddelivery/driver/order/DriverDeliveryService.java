package vn.hutech.fooddelivery.driver.order;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsCalculator;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsRepository;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.order.OrderStatusFlow;

import java.util.Set;

@Service
public class DriverDeliveryService {
    private static final Set<String> TARGETS = Set.of("PICKED_UP", "DELIVERING", "DELIVERED");
    private final DriverDeliveryRepository deliveries;
    private final OrderRepository orders;
    private final DriverEarningsRepository earnings;

    public DriverDeliveryService(DriverDeliveryRepository deliveries, OrderRepository orders,
                                 DriverEarningsRepository earnings) {
        this.deliveries = deliveries;
        this.orders = orders;
        this.earnings = earnings;
    }

    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DriverOrderStatusResponse changeStatus(long userId, long orderId, String target) {
        if (orderId <= 0 || target == null || !TARGETS.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID hoặc trạng thái không hợp lệ");
        }
        long driverId = deliveries.findDriverId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        var order = deliveries.lockOrder(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        if (order.driverId() == null) throw new ConflictException("Đơn hàng chưa được gán tài xế");
        if (order.driverId() != driverId) throw new ForbiddenException("Đơn hàng không thuộc tài xế hiện tại");
        if (!deliveries.lockDriver(driverId, userId)) {
            throw new ConflictException("Hồ sơ tài xế đã thay đổi");
        }
        var accepted = deliveries.lockAcceptedDriverIds(orderId);
        if (accepted.size() != 1 || accepted.getFirst() != driverId) {
            throw new ConflictException("Assignment ACCEPTED không khớp tài xế của đơn hàng");
        }

        var earning = earnings.findByOrderIdForUpdate(orderId);
        if ("DELIVERED".equals(order.status())) {
            // Replay chi doc so tien da luu, khong tinh lai/backfill don cu.
            if (earning.isEmpty() || earning.get().driverId() != driverId
                    || earning.get().amount().signum() < 0 || earning.get().bonus().signum() < 0
                    || order.deliveredAt() == null) {
                throw new ConflictException("Đơn đã giao có dữ liệu thu nhập hoặc thời điểm không nhất quán");
            }
        } else if (earning.isPresent() || order.deliveredAt() != null) {
            throw new ConflictException("Đơn chưa hoàn thành có dữ liệu thu nhập hoặc thời điểm không nhất quán");
        }
        if (target.equals(order.status())) {
            if (!deliveries.hasDriverHistory(orderId, target, userId)) {
                throw new ConflictException("Trạng thái đã lưu thiếu lịch sử cập nhật của tài xế");
            }
            return order.response();
        }
        if (!OrderStatusFlow.canChange(OrderStatusFlow.Actor.DRIVER, order.status(), target)) {
            throw new ConflictException("Không thể chuyển đơn từ \"" + OrderStatusFlow.label(order.status())
                    + "\" sang \"" + OrderStatusFlow.label(target) + "\"");
        }
        if (orders.updateStatus(orderId, order.status(), target, null) != 1) {
            throw new ConflictException("Đơn vừa được cập nhật, vui lòng tải lại");
        }
        orders.insertStatusHistory(orderId, target, userId, null);
        if ("DELIVERED".equals(target)) {
            try {
                earnings.insert(orderId, driverId, DriverEarningsCalculator.baseEarning(order.deliveryFee()));
            } catch (DuplicateKeyException duplicate) {
                throw new ConflictException("Đơn hàng đã có thu nhập; không thể ghi đè");
            }
        }
        return deliveries.lockOrder(orderId).orElseThrow().response();
    }
}
