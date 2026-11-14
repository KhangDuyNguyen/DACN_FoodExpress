package vn.hutech.fooddelivery.order;

import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.order.OrderStatusFlow.Actor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit test luat chuyen trang thai: thu MOI cap (tu, sang) cho tung vai tro. */
class OrderStatusFlowTest {

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Kiem tra day du enum DB, gom ca trang thai khong duoc giao.
    private static final List<String> STATUSES = List.of("PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING",
            "READY_FOR_PICKUP", "PICKED_UP", "DELIVERING", "DELIVERED", "CANCELLED", "REJECTED");

    // Cac buoc hop le, viet dang "TU>SANG"
    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]:
    // Kiem tra canh PREPARING -> READY_FOR_PICKUP cua merchant, giu cac canh giao cua driver.
    private static final Set<String> MERCHANT_ALLOWED = Set.of(
            "PLACED>PREPARING", "PLACED>CANCELLED",
            "PREPARING>CANCELLED", "PREPARING>READY_FOR_PICKUP");
    private static final Set<String> CUSTOMER_ALLOWED = Set.of("PLACED>CANCELLED");

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Tai xe chi co 4 canh giao duoc duyet, khong huy/nhan mon ho merchant.
    @Test
    void driverCanOnlyFollowEveryDeliveryStep() {
        var allowed = Set.of("PREPARING>PICKED_UP", "READY_FOR_PICKUP>PICKED_UP",
                "PICKED_UP>DELIVERING", "DELIVERING>DELIVERED");
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                assertEquals(allowed.contains(from + ">" + to), OrderStatusFlow.canChange(Actor.DRIVER, from, to),
                        from + " -> " + to);
            }
        }
    }

    @Test
    void merchantCanOnlyMoveForwardOrCancelBeforeDelivering() {
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                boolean expected = MERCHANT_ALLOWED.contains(from + ">" + to);
                assertEquals(expected, OrderStatusFlow.canChange(Actor.MERCHANT, from, to),
                        "Chu quan " + from + " -> " + to);
            }
        }
    }

    @Test
    void customerCanOnlyCancelWhilePlaced() {
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                boolean expected = CUSTOMER_ALLOWED.contains(from + ">" + to);
                assertEquals(expected, OrderStatusFlow.canChange(Actor.CUSTOMER, from, to),
                        "Khach " + from + " -> " + to);
            }
        }
    }

    @Test
    void finishedOrdersCannotChangeAnymore() {
        for (String to : STATUSES) {
            assertEquals(false, OrderStatusFlow.canChange(Actor.MERCHANT, "DELIVERED", to));
            assertEquals(false, OrderStatusFlow.canChange(Actor.MERCHANT, "CANCELLED", to));
        }
    }

    @Test
    void labelsAreVietnamese() {
        assertEquals("Chờ xác nhận", OrderStatusFlow.label("PLACED"));
        assertEquals("Hoàn thành", OrderStatusFlow.label("DELIVERED"));
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Nhan giao hang moi van hien tieng Viet.
        assertEquals("Đã lấy món", OrderStatusFlow.label("PICKED_UP"));
        assertEquals("Sẵn sàng lấy món", OrderStatusFlow.label("READY_FOR_PICKUP"));
    }
}
