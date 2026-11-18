package vn.hutech.fooddelivery.order;

import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.order.OrderStatusFlow.Actor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Unit test luat chuyen trang thai: thu MOI cap (tu, sang) cho tung vai tro, co va khong co tai xe. */
class OrderStatusFlowTest {

    private static final List<String> STATUSES =
            List.of("PLACED", "PREPARING", "READY_FOR_PICKUP", "DELIVERING", "DELIVERED", "CANCELLED");

    // Cac buoc hop le, viet dang "TU>SANG"
    private static final Set<String> MERCHANT_NO_DRIVER = Set.of(
            "PLACED>PREPARING", "PLACED>CANCELLED",
            "PREPARING>READY_FOR_PICKUP", "PREPARING>CANCELLED",
            "READY_FOR_PICKUP>DELIVERING", "DELIVERING>DELIVERED");
    // Don da co tai xe: chu quan mat 2 buoc giao hang
    private static final Set<String> MERCHANT_WITH_DRIVER = Set.of(
            "PLACED>PREPARING", "PLACED>CANCELLED",
            "PREPARING>READY_FOR_PICKUP", "PREPARING>CANCELLED");
    private static final Set<String> DRIVER_WITH_DRIVER = Set.of(
            "READY_FOR_PICKUP>DELIVERING", "DELIVERING>DELIVERED");
    private static final Set<String> CUSTOMER_ALLOWED = Set.of("PLACED>CANCELLED");

    private static void assertTable(Actor actor, boolean hasDriver, Set<String> allowed) {
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                boolean expected = allowed.contains(from + ">" + to);
                assertEquals(expected, OrderStatusFlow.canChange(actor, from, to, hasDriver),
                        actor + (hasDriver ? " (co tai xe) " : " (chua co tai xe) ") + from + " -> " + to);
            }
        }
    }

    @Test
    void merchantWithoutDriverDoesEveryStepIncludingDelivery() {
        assertTable(Actor.MERCHANT, false, MERCHANT_NO_DRIVER);
    }

    @Test
    void merchantCannotDoDeliveryStepsOnceADriverIsAssigned() {
        assertTable(Actor.MERCHANT, true, MERCHANT_WITH_DRIVER);
    }

    @Test
    void driverOnlyDoesDeliveryStepsAndOnlyWhenAssigned() {
        assertTable(Actor.DRIVER, true, DRIVER_WITH_DRIVER);
        assertTable(Actor.DRIVER, false, Set.of());
    }

    @Test
    void customerCanOnlyCancelWhilePlaced() {
        assertTable(Actor.CUSTOMER, false, CUSTOMER_ALLOWED);
        assertTable(Actor.CUSTOMER, true, CUSTOMER_ALLOWED);
    }

    @Test
    void finishedOrdersCannotChangeAnymore() {
        for (Actor actor : Actor.values()) {
            for (String to : STATUSES) {
                assertFalse(OrderStatusFlow.canChange(actor, "DELIVERED", to, true));
                assertFalse(OrderStatusFlow.canChange(actor, "CANCELLED", to, false));
            }
        }
    }

    @Test
    void labelsAreVietnamese() {
        assertEquals("Chờ xác nhận", OrderStatusFlow.label("PLACED"));
        assertEquals("Chờ lấy món", OrderStatusFlow.label("READY_FOR_PICKUP"));
        assertEquals("Hoàn thành", OrderStatusFlow.label("DELIVERED"));
        // Trang thai ngoai tap dang dung thi giu nguyen ten
        assertEquals("PICKED_UP", OrderStatusFlow.label("PICKED_UP"));
    }
}
