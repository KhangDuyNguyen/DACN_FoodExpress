package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.driver.dto.DriverStatusRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverStatusServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 21, 0, 0);
    private static final BigDecimal LAT = new BigDecimal("10.7769");
    private static final BigDecimal LNG = new BigDecimal("106.7009");

    @Mock
    DriverRepository driverRepository;
    @Mock
    DriverLocationRepository driverLocationRepository;
    DriverService service;

    @BeforeEach
    void setUp() {
        // Test co dinh bien 60 giay, ke ca khi dong ho thuc te dang o phan le cua giay.
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T14:00:00.987Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new DriverService(driverRepository, driverLocationRepository, clock);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 59, 60})
    void approvedDriverWithLocationUpToExactly60SecondsOldCanGoOnline(long ageSeconds) {
        given(new DriverRepository.DriverStatusRow(7, "APPROVED", false, LAT, LNG, NOW.minusSeconds(ageSeconds)));
        when(driverRepository.updateOnline(7, true)).thenReturn(1);

        assertEquals(true, service.updateStatus(42, new DriverStatusRequest(true)).isOnline());
        var order = inOrder(driverRepository);
        order.verify(driverRepository).findStatusByUserIdForUpdate(42);
        order.verify(driverRepository).updateOnline(7, true);
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(longs = {61, 120})
    void locationOlderThan60SecondsIsRejected(long ageSeconds) {
        given(new DriverRepository.DriverStatusRow(7, "APPROVED", false, LAT, LNG, NOW.minusSeconds(ageSeconds)));
        assertThrows(ConflictException.class, () -> service.updateStatus(42, new DriverStatusRequest(true)));
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 60})
    void futureLocationIsRejected(long futureSeconds) {
        given(new DriverRepository.DriverStatusRow(7, "APPROVED", false, LAT, LNG, NOW.plusSeconds(futureSeconds)));
        assertThrows(ConflictException.class, () -> service.updateStatus(42, new DriverStatusRequest(true)));
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED"})
    void nonApprovedDriverCannotGoOnlineEvenWithFreshGps(String status) {
        given(new DriverRepository.DriverStatusRow(7, status, false, LAT, LNG, NOW));
        assertThrows(ForbiddenException.class, () -> service.updateStatus(42, new DriverStatusRequest(true)));
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    @ParameterizedTest
    @MethodSource("invalidLocations")
    void missingOrInvalidStoredLocationCannotEnableOnline(BigDecimal lat, BigDecimal lng, LocalDateTime at) {
        given(new DriverRepository.DriverStatusRow(7, "APPROVED", false, lat, lng, at));
        assertThrows(ConflictException.class, () -> service.updateStatus(42, new DriverStatusRequest(true)));
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    static Stream<Arguments> invalidLocations() {
        return Stream.of(
                Arguments.of(null, LNG, NOW), Arguments.of(LAT, null, NOW), Arguments.of(LAT, LNG, null),
                Arguments.of(new BigDecimal("90.0000001"), LNG, NOW),
                Arguments.of(new BigDecimal("-90.0000001"), LNG, NOW),
                Arguments.of(LAT, new BigDecimal("180.0000001"), NOW),
                Arguments.of(LAT, new BigDecimal("-180.0000001"), NOW)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "APPROVED", "REJECTED", "SUSPENDED"})
    void everyApprovalStatusCanGoOfflineWithoutGps(String status) {
        given(new DriverRepository.DriverStatusRow(7, status, true, null, null, null));
        when(driverRepository.updateOnline(7, false)).thenReturn(1);

        assertEquals(false, service.updateStatus(42, new DriverStatusRequest(false)).isOnline());
        verify(driverRepository).updateOnline(7, false);
    }

    @Test
    void repeatedOfflineReturnsPersistedFalseWithoutRequiringGpsOrWritingAgain() {
        given(new DriverRepository.DriverStatusRow(7, "PENDING", false, null, null, NOW.plusSeconds(60)));
        assertEquals(false, service.updateStatus(42, new DriverStatusRequest(false)).isOnline());
        assertEquals(false, service.updateStatus(42, new DriverStatusRequest(false)).isOnline());
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    @Test
    void repeatedOnlineStillChecksFreshGpsAndDoesNotAutomaticallyOfflineStaleDriver() {
        given(new DriverRepository.DriverStatusRow(7, "APPROVED", true, LAT, LNG, NOW.minusSeconds(61)));
        assertThrows(ConflictException.class, () -> service.updateStatus(42, new DriverStatusRequest(true)));
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    @Test
    void missingProfileReturns404() {
        when(driverRepository.findStatusByUserIdForUpdate(42)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.updateStatus(42, new DriverStatusRequest(false)));
        verify(driverRepository, never()).updateOnline(anyLong(), anyBoolean());
    }

    @Test
    void unsuccessfulDatabaseUpdateIsNotReportedAsSuccess() {
        given(new DriverRepository.DriverStatusRow(7, "APPROVED", false, LAT, LNG, NOW));
        when(driverRepository.updateOnline(7, true)).thenReturn(0);
        assertThrows(ConflictException.class, () -> service.updateStatus(42, new DriverStatusRequest(true)));
    }

    private void given(DriverRepository.DriverStatusRow row) {
        when(driverRepository.findStatusByUserIdForUpdate(42)).thenReturn(Optional.of(row));
    }
}
