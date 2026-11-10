package vn.hutech.fooddelivery.order;

import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.order.dto.PriceBreakdown;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit test phep tinh tien, khong can database. */
class OrderPricingTest {

    // Giong seed V20261107_02: 15.000d cho 2 km dau, 5.000d moi km tiep theo
    private static final DeliveryFeeRule RULE =
            new DeliveryFeeRule(new BigDecimal("15000"), new BigDecimal("2.00"), new BigDecimal("5000"));

    private static CartLine line(long price, int quantity) {
        return new CartLine(1, 1, "Mon test", null, BigDecimal.valueOf(price), quantity, null, true);
    }

    /** So sanh tien theo gia tri (compareTo), khong phu thuoc so chu so le cua BigDecimal. */
    private static void assertMoney(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                "Mong doi " + expected + " nhung nhan " + actual);
    }

    @Test
    void subtotalIsSumOfUnitPriceTimesQuantity() {
        // 2 x 45.000 + 1 x 8.000 = 98.000
        assertMoney(98000, OrderPricing.subtotal(List.of(line(45000, 2), line(8000, 1))));
        assertMoney(0, OrderPricing.subtotal(List.of()));
    }

    @Test
    void feeWithinBaseDistanceIsBaseFee() {
        assertMoney(15000, OrderPricing.deliveryFee(new BigDecimal("0.00"), RULE));
        assertMoney(15000, OrderPricing.deliveryFee(new BigDecimal("1.80"), RULE));
        assertMoney(15000, OrderPricing.deliveryFee(new BigDecimal("2.00"), RULE));
    }

    @Test
    void extraKilometersAreRoundedUp() {
        // Vuot 0,01 km cung tinh them 1 km
        assertMoney(20000, OrderPricing.deliveryFee(new BigDecimal("2.01"), RULE));
        assertMoney(20000, OrderPricing.deliveryFee(new BigDecimal("2.10"), RULE));
        assertMoney(20000, OrderPricing.deliveryFee(new BigDecimal("3.00"), RULE));
        assertMoney(25000, OrderPricing.deliveryFee(new BigDecimal("3.01"), RULE));
        assertMoney(30000, OrderPricing.deliveryFee(new BigDecimal("5.00"), RULE));
    }

    @Test
    void totalAddsFeeAndSubtractsDiscountButNeverNegative() {
        assertMoney(105000, OrderPricing.total(new BigDecimal("90000"), new BigDecimal("15000"), BigDecimal.ZERO));
        assertMoney(95000, OrderPricing.total(new BigDecimal("90000"), new BigDecimal("15000"), new BigDecimal("10000")));
        assertMoney(0, OrderPricing.total(new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("20000")));
    }

    @Test
    void distanceIsRoundedToTwoDecimals() {
        BigDecimal samePoint = OrderPricing.distanceKm(10.7812, 106.7008, 10.7812, 106.7008);
        assertEquals(new BigDecimal("0.00"), samePoint);

        // 2 chi nhanh Com Nha Demo (Quan 1 va Quan 3) cach nhau khoang 1,5 km
        BigDecimal branches = OrderPricing.distanceKm(10.7812, 106.7008, 10.7765, 106.6880);
        assertEquals(2, branches.scale());
        assertEquals(0, branches.compareTo(new BigDecimal("1.49")));
    }

    @Test
    void calculateCombinesAllSteps() {
        PriceBreakdown price = OrderPricing.calculate(
                List.of(line(45000, 2), line(5000, 1)),
                10.7812, 106.7008, 10.7812, 106.7008, RULE);

        assertMoney(95000, price.subtotal());
        assertMoney(15000, price.deliveryFee());
        assertMoney(0, price.discountAmount());
        assertMoney(110000, price.totalAmount());
        assertEquals(new BigDecimal("0.00"), price.distanceKm());
    }
}
