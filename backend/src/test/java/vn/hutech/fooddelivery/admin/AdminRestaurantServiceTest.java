package vn.hutech.fooddelivery.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.admin.dto.AdminRestaurantResponse;
import vn.hutech.fooddelivery.common.ConflictException;
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
class AdminRestaurantServiceTest {

    @Mock
    private AdminRestaurantRepository adminRestaurantRepository;

    @InjectMocks
    private AdminRestaurantService adminRestaurantService;

    private AdminRestaurantResponse sampleRestaurant(long id, String restStatus, String merchantStatus) {
        return new AdminRestaurantResponse(
                id,
                1L,
                "Demo Merchant Group",
                10L,
                "Nguyen Van Chu Quan",
                "0901234567",
                merchantStatus,
                "Pho Bo Ha Noi",
                "Mon ngon Ha Noi",
                "0281234567",
                "123 Nguyen Trai, Q1, TP.HCM",
                BigDecimal.valueOf(10.77),
                BigDecimal.valueOf(106.69),
                "logo.png",
                "cover.png",
                "OPEN",
                restStatus,
                BigDecimal.valueOf(30000),
                20,
                BigDecimal.valueOf(4.5),
                100,
                LocalDateTime.now().minusDays(5),
                LocalDateTime.now());
    }

    @Test
    @DisplayName("Lấy danh sách nhà hàng có phân trang thành công")
    void getRestaurants_success() {
        when(adminRestaurantRepository.findRestaurants("Pho", "PENDING", 0, 10))
                .thenReturn(List.of(sampleRestaurant(1L, "PENDING", "APPROVED")));
        when(adminRestaurantRepository.countRestaurants("Pho", "PENDING"))
                .thenReturn(1L);

        PageResponse<AdminRestaurantResponse> res = adminRestaurantService.getRestaurants("Pho", "PENDING", 0, 10);

        assertNotNull(res);
        assertEquals(1, res.totalItems());
        assertEquals("Pho Bo Ha Noi", res.items().get(0).name());
    }

    @Test
    @DisplayName("Duyệt nhà hàng thành APPROVED thành công khi chủ quán đã APPROVED")
    void updateStatus_approved_whenMerchantApproved_success() {
        when(adminRestaurantRepository.findById(1L))
                .thenReturn(Optional.of(sampleRestaurant(1L, "PENDING", "APPROVED")))
                .thenReturn(Optional.of(sampleRestaurant(1L, "APPROVED", "APPROVED")));
        when(adminRestaurantRepository.updateStatus(1L, "APPROVED")).thenReturn(1);

        AdminRestaurantResponse updated = adminRestaurantService.updateStatus(1L, "APPROVED");

        assertNotNull(updated);
        assertEquals("APPROVED", updated.status());
        verify(adminRestaurantRepository).updateStatus(1L, "APPROVED");
    }

    @Test
    @DisplayName("Không được duyệt nhà hàng khi chủ quán chưa được duyệt (PENDING) -> ném ConflictException")
    void updateStatus_approved_whenMerchantPending_throwsConflict() {
        when(adminRestaurantRepository.findById(1L))
                .thenReturn(Optional.of(sampleRestaurant(1L, "PENDING", "PENDING")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> adminRestaurantService.updateStatus(1L, "APPROVED"));

        assertTrue(ex.getMessage().contains("chủ quán chưa được phê duyệt"));
        verify(adminRestaurantRepository, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("Từ chối nhà hàng thành REJECTED thành công kể cả khi chủ quán chưa duyệt")
    void updateStatus_rejected_success() {
        when(adminRestaurantRepository.findById(1L))
                .thenReturn(Optional.of(sampleRestaurant(1L, "PENDING", "PENDING")))
                .thenReturn(Optional.of(sampleRestaurant(1L, "REJECTED", "PENDING")));
        when(adminRestaurantRepository.updateStatus(1L, "REJECTED")).thenReturn(1);

        AdminRestaurantResponse updated = adminRestaurantService.updateStatus(1L, "REJECTED");

        assertNotNull(updated);
        assertEquals("REJECTED", updated.status());
        verify(adminRestaurantRepository).updateStatus(1L, "REJECTED");
    }

    @Test
    @DisplayName("Cập nhật nhà hàng không tồn tại ném NotFoundException")
    void updateStatus_notFound_throwsNotFound() {
        when(adminRestaurantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminRestaurantService.updateStatus(999L, "APPROVED"));
    }
}
