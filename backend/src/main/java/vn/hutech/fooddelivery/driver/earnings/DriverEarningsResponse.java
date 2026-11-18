package vn.hutech.fooddelivery.driver.earnings;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DriverEarningsResponse(LocalDate from, LocalDate to, String timeZone,
                                     BigDecimal baseAmount, BigDecimal bonus, BigDecimal totalAmount,
                                     List<Item> items) {
    public record Item(long orderId, String orderCode, BigDecimal amount, BigDecimal bonus,
                       BigDecimal totalAmount, LocalDateTime createdAt) {
    }
}
