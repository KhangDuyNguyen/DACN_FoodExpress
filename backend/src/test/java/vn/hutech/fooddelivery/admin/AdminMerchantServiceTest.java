package vn.hutech.fooddelivery.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.admin.dto.AdminMerchantResponse;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMerchantServiceTest {

    @Mock
    private AdminMerchantRepository adminMerchantRepository;

    @InjectMocks
    private AdminMerchantService adminMerchantService;

    private AdminMerchantResponse sampleMerchant(long id, String status) {
        return new AdminMerchantResponse(
                id,
                10L,
                "Nguyen Van Chu Quan",
                "merchant@test.com",
                "0901234567",
                "Quan Com Tam Sai Gon",
                "0312345678",
                "0901234567",
                status,
                2,
                LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("Lấy danh sách chủ quán thành công có phân trang")
    void getMerchants_returnsPagedMerchants() {
        when(adminMerchantRepository.findMerchants("Com Tam", "PENDING", 0, 10))
                .thenReturn(List.of(sampleMerchant(1L, "PENDING")));
        when(adminMerchantRepository.countMerchants("Com Tam", "PENDING"))
                .thenReturn(1L);

        PageResponse<AdminMerchantResponse> response = adminMerchantService.getMerchants("Com Tam", "PENDING", 0, 10);

        assertNotNull(response);
        assertEquals(1, response.totalItems());
        assertEquals(1, response.items().size());
        assertEquals("Quan Com Tam Sai Gon", response.items().get(0).businessName());
        assertEquals("PENDING", response.items().get(0).status());
    }

    @Test
    @DisplayName("Lấy chi tiết chủ quán theo ID thành công")
    void getMerchantById_success() {
        when(adminMerchantRepository.findById(1L)).thenReturn(Optional.of(sampleMerchant(1L, "PENDING")));

        AdminMerchantResponse res = adminMerchantService.getMerchantById(1L);

        assertNotNull(res);
        assertEquals(1L, res.id());
        assertEquals("Nguyen Van Chu Quan", res.ownerFullName());
    }

    @Test
    @DisplayName("Lấy chi tiết chủ quán không tồn tại ném NotFoundException")
    void getMerchantById_notFound() {
        when(adminMerchantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminMerchantService.getMerchantById(999L));
    }

    @Test
    @DisplayName("Duyệt chủ quán thành APPROVED thành công")
    void updateStatus_approved_success() {
        when(adminMerchantRepository.findById(1L))
                .thenReturn(Optional.of(sampleMerchant(1L, "PENDING")))
                .thenReturn(Optional.of(sampleMerchant(1L, "APPROVED")));
        when(adminMerchantRepository.updateStatus(1L, "APPROVED")).thenReturn(1);

        AdminMerchantResponse updated = adminMerchantService.updateStatus(1L, "APPROVED");

        assertNotNull(updated);
        assertEquals("APPROVED", updated.status());
        verify(adminMerchantRepository).updateStatus(1L, "APPROVED");
    }

    @Test
    @DisplayName("Từ chối chủ quán thành REJECTED thành công")
    void updateStatus_rejected_success() {
        when(adminMerchantRepository.findById(1L))
                .thenReturn(Optional.of(sampleMerchant(1L, "PENDING")))
                .thenReturn(Optional.of(sampleMerchant(1L, "REJECTED")));
        when(adminMerchantRepository.updateStatus(1L, "REJECTED")).thenReturn(1);

        AdminMerchantResponse updated = adminMerchantService.updateStatus(1L, "REJECTED");

        assertNotNull(updated);
        assertEquals("REJECTED", updated.status());
        verify(adminMerchantRepository).updateStatus(1L, "REJECTED");
    }

    @Test
    @DisplayName("Cập nhật trạng thái không hợp lệ ném IllegalArgumentException")
    void updateStatus_invalidStatus_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> adminMerchantService.updateStatus(1L, "UNKNOWN"));
        verify(adminMerchantRepository, never()).updateStatus(anyLong(), anyString());
    }
}
