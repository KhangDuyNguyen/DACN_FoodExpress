package vn.hutech.fooddelivery.driver.earnings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** An - Tuan 5: amount = 80% phi goc da luu, VND nguyen HALF_UP; bonus luu rieng. */
public final class DriverEarningsCalculator {
    private static final BigDecimal DRIVER_SHARE = new BigDecimal("0.80");

    private DriverEarningsCalculator() {
    }

    public static BigDecimal baseEarning(BigDecimal originalDeliveryFee) {
        if (originalDeliveryFee == null || originalDeliveryFee.signum() < 0) {
            throw new IllegalStateException("Phí giao hàng gốc không hợp lệ");
        }
        return originalDeliveryFee.multiply(DRIVER_SHARE).setScale(0, RoundingMode.HALF_UP);
    }
}
