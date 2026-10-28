package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.driver.dto.DriverProfileResponse;
import vn.hutech.fooddelivery.driver.dto.UpdateDriverProfileRequest;
import vn.hutech.fooddelivery.driver.dto.DriverLocationRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    DriverRepository driverRepository;

    @Mock
    DriverLocationRepository driverLocationRepository;

    @InjectMocks
    DriverService driverService;

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "APPROVED", "REJECTED", "SUSPENDED"})
    void profileCanBeReadRegardlessOfApprovalStatus(String status) {
        var profile = new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                "BICYCLE", null, status, false);
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(profile));

        assertEquals(profile, driverService.getProfile(42));
        verify(driverRepository).findProfileByUserId(42);
    }

    @Test
    void missingDriverProfileThrowsNotFound() {
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> driverService.getProfile(42));
        assertEquals("Không tìm thấy hồ sơ tài xế", ex.getMessage());
    }

    @Test
    void pendingDriverUpdatesOwnVehicleAndReturnsProfileAfterUpdate() {
        var before = new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                "MOTORBIKE", "59A1-12345", "PENDING", false);
        var after = new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                "BICYCLE", null, "PENDING", false);
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(before), Optional.of(after));
        when(driverRepository.updatePendingProfile(42, "BICYCLE", null)).thenReturn(1);

        assertEquals(after, driverService.updateProfile(42, new UpdateDriverProfileRequest("BICYCLE", "  ")));
        verify(driverRepository).updatePendingProfile(42, "BICYCLE", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "SUSPENDED"})
    void nonPendingDriverCannotUpdateVehicle(String status) {
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(
                new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                        "MOTORBIKE", "59A1-12345", status, false)));

        assertThrows(ForbiddenException.class,
                () -> driverService.updateProfile(42, new UpdateDriverProfileRequest("BICYCLE", null)));
        verify(driverRepository, never()).updatePendingProfile(anyLong(), anyString(), nullable(String.class));
    }

    @Test
    void updateWithoutProfileReturnsNotFoundWithoutWriting() {
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> driverService.updateProfile(42, new UpdateDriverProfileRequest("BICYCLE", null)));
        verify(driverRepository, never()).updatePendingProfile(anyLong(), anyString(), nullable(String.class));
    }

    @Test
    void statusChangedBetweenReadAndWriteReturnsConflict() {
        when(driverRepository.findProfileByUserId(42)).thenReturn(Optional.of(
                new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                        "MOTORBIKE", "59A1-12345", "PENDING", false)));
        when(driverRepository.updatePendingProfile(42, "BICYCLE", null)).thenReturn(0);

        assertThrows(ConflictException.class,
                () -> driverService.updateProfile(42, new UpdateDriverProfileRequest("BICYCLE", null)));
        verify(driverRepository, times(1)).findProfileByUserId(42);
    }

    @Test
    void locationUsesResolvedDriverIdAndSameServerTimeForBothWrites() {
        when(driverRepository.findIdByUserIdForUpdate(42)).thenReturn(Optional.of(7L));
        when(driverRepository.updateLocation(eq(7L), any(), any(), any())).thenReturn(1);
        var before = LocalDateTime.now().minusSeconds(1);

        var response = driverService.recordLocation(42,
                new DriverLocationRequest(new BigDecimal("10.7769"), new BigDecimal("106.7009")));

        assertTrue(response.recordedAt().isAfter(before));
        assertTrue(response.recordedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertEquals(0, response.recordedAt().getNano());
        var order = inOrder(driverRepository, driverLocationRepository);
        order.verify(driverRepository).findIdByUserIdForUpdate(42);
        order.verify(driverLocationRepository).insert(7, response.lat(), response.lng(), response.recordedAt());
        order.verify(driverRepository).updateLocation(7, response.lat(), response.lng(), response.recordedAt());
        order.verifyNoMoreInteractions();
    }

    @Test
    void locationWithoutDriverProfileDoesNotWrite() {
        when(driverRepository.findIdByUserIdForUpdate(42)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> driverService.recordLocation(42,
                new DriverLocationRequest(BigDecimal.ZERO, BigDecimal.ZERO)));
        verifyNoInteractions(driverLocationRepository);
        verify(driverRepository, never()).updateLocation(anyLong(), any(), any(), any());
    }
}
