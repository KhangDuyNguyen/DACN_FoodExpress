package vn.hutech.fooddelivery.driver.earnings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DriverEarningsCalculatorTest {
    @ParameterizedTest
    @CsvSource({"0,0", "15000,12000", "15001,12001", "15002,12002", "15003,12002",
            "15004,12003", "1,1", "2,2", "3,2", "999999999999,799999999999"})
    void eightyPercentIsRoundedToWholeVndHalfUp(String fee, String expected) {
        assertEquals(new BigDecimal(expected), DriverEarningsCalculator.baseEarning(new BigDecimal(fee)));
    }

    @Test
    void exactHalfRoundsUp() {
        assertEquals(new BigDecimal("1"), DriverEarningsCalculator.baseEarning(new BigDecimal("0.625")));
    }

    @Test
    void missingOrNegativeOriginalFeeIsAnInvariantFailure() {
        assertThrows(IllegalStateException.class, () -> DriverEarningsCalculator.baseEarning(null));
        assertThrows(IllegalStateException.class, () -> DriverEarningsCalculator.baseEarning(new BigDecimal("-1")));
    }
}
