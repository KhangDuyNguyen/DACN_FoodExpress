package vn.hutech.fooddelivery.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.admin.dto.AdminDriverResponse;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDriverServiceTest {

    @Mock
    private AdminDriverRepository adminDriverRepository;

    @InjectMocks
    private AdminDriverService adminDriverService;

    private AdminDriverResponse sampleDriver(long id, String status, boolean isOnline) {
        return new AdminDriverResponse(
                id,
                20L,
                "Tran Van Tai Xe",
                "0987654321",
                "driver@test.com",
                "avatar.jpg",
                "MOTORBIKE",
                "59-X1 123.45",
                status,
                isOnline,
                BigDecimal.valueOf(10.77),
                BigDecimal.valueOf(106.69),
                BigDecimal.valueOf(4.8),
                LocalDateTime.now().minusDays(10));
    }

    @Test
    @DisplayName("Lấy danh sách tài xế phân trang thành công")
    void getDrivers_success() {
        when(adminDriverRepository.findDrivers("Tai Xe", "PENDING", 0, 10))
                .thenReturn(List.of(sampleDriver(1L, "PENDING", false)));
        when(adminDriverRepository.countDrivers("Tai Xe", "PENDING"))
                .thenReturn(1L);

        PageResponse<AdminDriverResponse> res = adminDriverService.getDrivers("Tai Xe", "PENDING", 0, 10);

        assertNotNull(res);
        assertEquals(1, res.totalItems());
        assertEquals("Tran Van Tai Xe", res.items().get(0).fullName());
    }

    @Test
    @DisplayName("Lấy chi tiết tài xế thành công")
    void getDriverById_success() {
        when(adminDriverRepository.findById(1L)).thenReturn(Optional.of(sampleDriver(1L, "PENDING", false)));

        AdminDriverResponse res = adminDriverService.getDriverById(1L);

        assertNotNull(res);
        assertEquals("59-X1 123.45", res.licensePlate());
    }

    @Test
    @DisplayName("Duyệt tài xế thành APPROVED thành công")
    void updateStatus_approved_success() {
        when(adminDriverRepository.findById(1L))
                .thenReturn(Optional.of(sampleDriver(1L, "PENDING", false)))
                .thenReturn(Optional.of(sampleDriver(1L, "APPROVED", false)));
        when(adminDriverRepository.updateStatus(1L, "APPROVED")).thenReturn(1);

        AdminDriverResponse res = adminDriverService.updateStatus(1L, "APPROVED");

        assertNotNull(res);
        assertEquals("APPROVED", res.status());
        verify(adminDriverRepository).updateStatus(1L, "APPROVED");
    }

    @Test
    @DisplayName("Từ chối tài xế thành REJECTED thành công")
    void updateStatus_rejected_success() {
        when(adminDriverRepository.findById(1L))
                .thenReturn(Optional.of(sampleDriver(1L, "APPROVED", true)))
                .thenReturn(Optional.of(sampleDriver(1L, "REJECTED", false)));
        when(adminDriverRepository.updateStatus(1L, "REJECTED")).thenReturn(1);

        AdminDriverResponse res = adminDriverService.updateStatus(1L, "REJECTED");

        assertNotNull(res);
        assertEquals("REJECTED", res.status());
        verify(adminDriverRepository).updateStatus(1L, "REJECTED");
    }

    @Test
    @DisplayName("Cập nhật tài xế không tồn tại ném NotFoundException")
    void updateStatus_notFound_throwsNotFound() {
        when(adminDriverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminDriverService.updateStatus(999L, "APPROVED"));
    }
}
