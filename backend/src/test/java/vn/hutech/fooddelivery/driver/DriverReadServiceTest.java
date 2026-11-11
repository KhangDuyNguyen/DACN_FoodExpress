package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsReadRepository;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsResponse;
import vn.hutech.fooddelivery.driver.order.DriverOrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverReadServiceTest {
    @Mock DriverOrderRepository orders;
    @Mock DriverEarningsReadRepository earnings;
    DriverReadService service;

    @BeforeEach void setup() { service = new DriverReadService(orders, earnings); }

    @Test void unboundedReadUsesDriverIdAndSumsStoredBaseAndBonus() {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        var item = new DriverEarningsResponse.Item(901, "OLD", new BigDecimal("123"), new BigDecimal("7"),
                new BigDecimal("130"), null);
        when(earnings.findEarnings(7, null, null)).thenReturn(List.of(item));
        var response = service.earnings(42, null, null);
        assertNull(response.from()); assertNull(response.to());
        assertEquals("Asia/Ho_Chi_Minh", response.timeZone());
        assertEquals(new BigDecimal("123"), response.baseAmount());
        assertEquals(new BigDecimal("7"), response.bonus());
        assertEquals(new BigDecimal("130"), response.totalAmount());
        verify(earnings, never()).supportsDateBounds(any(), any());
    }

    @ParameterizedTest
    @CsvSource(value = {"2026-11-11|2026-11-11", "2026-11-11|NULL", "NULL|2026-11-11"}, delimiter = '|', nullValues = "NULL")
    void vietnamDatesUseInclusiveStartExclusiveNextDay(String from, String to) {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        Long first = from == null ? null : Instant.parse("2026-11-10T17:00:00Z").getEpochSecond();
        Long last = to == null ? null : Instant.parse("2026-11-11T17:00:00Z").getEpochSecond();
        when(earnings.supportsDateBounds(first, last)).thenReturn(true);
        var response = service.earnings(42, from, to);
        assertEquals(BigDecimal.ZERO, response.totalAmount());
        verify(earnings).findEarnings(7, first, last);
    }

    @ParameterizedTest @ValueSource(strings = {"", "2026-02-30", "2026-2-01", "2026-13-01", "11/11/2026",
            " 2026-11-11", "2026-11-11T00:00:00", "2026-11-11 OR 1=1"})
    void invalidDateNeverReachesDatabase(String date) {
        assertThrows(ResponseStatusException.class, () -> service.earnings(42, date, null));
        assertThrows(ResponseStatusException.class, () -> service.earnings(42, null, date));
        verifyNoInteractions(orders, earnings);
    }

    @Test void reversedDateRangeIs400BeforeDatabaseRead() {
        assertThrows(ResponseStatusException.class, () -> service.earnings(42, "2026-11-12", "2026-11-11"));
        verifyNoInteractions(orders, earnings);
    }

    @Test void databaseUnsupportedDateIsRejectedInsteadOfReturningAnUnboundedList() {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        assertThrows(ResponseStatusException.class, () -> service.earnings(42, "0001-01-01", null));
        verify(earnings, never()).findEarnings(anyLong(), any(), any());
    }

    @Test void bothEndpointsReturn404ForMissingDriverProfile() {
        assertThrows(NotFoundException.class, () -> service.earnings(42, null, null));
        assertThrows(NotFoundException.class, () -> service.history(42, 0, 10));
        verifyNoInteractions(earnings);
    }

    @ParameterizedTest @CsvSource({"-1,10", "0,0", "0,-1", "0,51"})
    void invalidPaginationIs400BeforeDatabaseRead(int page, int size) {
        assertThrows(ResponseStatusException.class, () -> service.history(42, page, size));
        verifyNoInteractions(orders, earnings);
    }

    @Test void historyUsesLongOffsetAndExistingPageResponseWithoutEarningsDependency() {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        when(orders.countDeliveredHistory(7)).thenReturn(3L);
        var page = service.history(42, Integer.MAX_VALUE, 50);
        assertEquals(3, page.totalItems()); assertEquals(1, page.totalPages());
        verify(orders).findDeliveredHistory(7, 50, 107374182350L);
        verifyNoInteractions(earnings);
    }
}
