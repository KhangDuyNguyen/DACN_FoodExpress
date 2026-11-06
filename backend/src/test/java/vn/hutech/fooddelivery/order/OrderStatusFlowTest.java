package vn.hutech.fooddelivery.order;

import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.order.OrderStatusFlow.Actor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit test luat chuyen trang thai: thu MOI cap (tu, sang) cho tung vai tro. */
class OrderStatusFlowTest {

    private static final List<String> STATUSES = List.of("PLACED", "PREPARING", "DELIVERING", "DELIVERED", "CANCELLED");

    // Cac buoc hop le, viet dang "TU>SANG"
    private static final Set<String> MERCHANT_ALLOWED = Set.of(
            "PLACED>PREPARING", "PLACED>CANCELLED",
            "PREPARING>DELIVERING", "PREPARING>CANCELLED",
            "DELIVERING>DELIVERED");
    private static final Set<String> CUSTOMER_ALLOWED = Set.of("PLACED>CANCELLED");

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
        // Trang thai ngoai tap tuan 3 thi giu nguyen ten
        assertEquals("PICKED_UP", OrderStatusFlow.label("PICKED_UP"));
    }
}
