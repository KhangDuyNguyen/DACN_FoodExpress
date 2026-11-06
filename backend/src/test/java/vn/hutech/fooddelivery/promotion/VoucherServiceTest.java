package vn.hutech.fooddelivery.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.promotion.dto.AdminVoucherRequest;
import vn.hutech.fooddelivery.promotion.dto.MerchantVoucherRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherResponse;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherJdbcRepository repository;

    @InjectMocks
    private VoucherService voucherService;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
    }

    private VoucherRow samplePercentVoucher(String code, VoucherStatus status, Integer usageLimit, int usedCount, int perUserLimit) {
        return new VoucherRow(
                1L,
                code,
                "Giảm 20% tối đa 30k",
                "Mô tả giảm giá",
                "PERCENT",
                BigDecimal.valueOf(20), // 20%
                BigDecimal.valueOf(100000), // minOrder 100k
                BigDecimal.valueOf(30000), // maxDiscount 30k
                now.minusDays(1),
                now.plusDays(5),
                usageLimit,
                perUserLimit,
                usedCount,
                null, // toàn sàn
                1L,
                status.name(),
                now.minusDays(2)
        );
    }

    private VoucherRow sampleFixedVoucher(String code, BigDecimal discountValue, BigDecimal minOrder) {
        return new VoucherRow(
                2L,
                code,
                "Giảm 25.000đ",
                "Mô tả",
                "FIXED",
                discountValue,
                minOrder,
                null,
                now.minusDays(1),
                now.plusDays(5),
                100,
                2,
                10,
                null,
                1L,
                "ACTIVE",
                now.minusDays(2)
        );
    }

    private VoucherRow sampleFreeShipVoucher(String code, BigDecimal maxDiscount) {
        return new VoucherRow(
                3L,
                code,
                "Freeship 15k",
                "Mô tả",
                "FREE_SHIP",
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(50000),
                maxDiscount,
                now.minusDays(1),
                now.plusDays(5),
                null,
                1,
                0,
                null,
                1L,
                "ACTIVE",
                now.minusDays(2)
        );
    }

    // =========================================================================
    // 1. KIỂM THỬ XÁC THỰC VÀ TÍNH GIẢM GIÁ (VALIDATE & 3 LOẠI VOUCHER)
    // =========================================================================

    @Test
    @DisplayName("Validate thất bại khi mã voucher không tồn tại")
    void validateVoucher_NotFound() {
        when(repository.findByCode("NON_EXIST")).thenReturn(Optional.empty());

        VoucherValidateRequest req = new VoucherValidateRequest("NON_EXIST", null, BigDecimal.valueOf(150000), BigDecimal.valueOf(15000));
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertFalse(res.valid());
        assertEquals("Mã voucher không tồn tại", res.message());
    }

    @Test
    @DisplayName("Validate thất bại khi voucher ở trạng thái EXPIRED hoặc PAUSED")
    void validateVoucher_ExpiredOrPausedStatus() {
        VoucherRow expired = samplePercentVoucher("EXPIRED_CODE", VoucherStatus.EXPIRED, 100, 5, 1);
        when(repository.findByCode("EXPIRED_CODE")).thenReturn(Optional.of(expired));

        VoucherValidateRequest req = new VoucherValidateRequest("EXPIRED_CODE", null, BigDecimal.valueOf(150000), null);
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertFalse(res.valid());
        assertTrue(res.message().contains("hết hạn"));
    }

    @Test
    @DisplayName("Validate thất bại khi đơn hàng chưa đạt giá trị tối thiểu")
    void validateVoucher_UnderMinOrder() {
        VoucherRow voucher = samplePercentVoucher("MIN_ORDER_TEST", VoucherStatus.ACTIVE, 100, 5, 1);
        when(repository.findByCode("MIN_ORDER_TEST")).thenReturn(Optional.of(voucher));

        // minOrder = 100.000, gửi lên 80.000
        VoucherValidateRequest req = new VoucherValidateRequest("MIN_ORDER_TEST", null, BigDecimal.valueOf(80000), null);
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertFalse(res.valid());
        assertTrue(res.message().contains("chưa đạt giá trị tối thiểu"));
    }

    @Test
    @DisplayName("Validate thất bại khi voucher đã hết lượt sử dụng toàn hệ thống")
    void validateVoucher_SystemUsageLimitReached() {
        // limit = 50, used = 50
        VoucherRow voucher = samplePercentVoucher("OUT_OF_STOCK", VoucherStatus.ACTIVE, 50, 50, 2);
        when(repository.findByCode("OUT_OF_STOCK")).thenReturn(Optional.of(voucher));

        VoucherValidateRequest req = new VoucherValidateRequest("OUT_OF_STOCK", null, BigDecimal.valueOf(150000), null);
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertFalse(res.valid());
        assertTrue(res.message().contains("hết lượt sử dụng trên hệ thống"));
    }

    @Test
    @DisplayName("Validate thất bại khi người dùng đã đạt giới hạn per_user_limit")
    void validateVoucher_PerUserLimitReached() {
        VoucherRow voucher = samplePercentVoucher("USER_LIMIT_TEST", VoucherStatus.ACTIVE, 100, 10, 1);
        when(repository.findByCode("USER_LIMIT_TEST")).thenReturn(Optional.of(voucher));
        when(repository.countUserUsage(voucher.id(), 100L)).thenReturn(1); // đã dùng 1 lần = perUserLimit

        VoucherValidateRequest req = new VoucherValidateRequest("USER_LIMIT_TEST", null, BigDecimal.valueOf(150000), null);
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertFalse(res.valid());
        assertTrue(res.message().contains("dùng hết số lượt cho phép"));
    }

    @Test
    @DisplayName("Validate thành công và tính giảm giá LOẠI 1: PERCENT (có áp dụng maxDiscount)")
    void validateVoucher_PercentSuccess_WithMaxDiscount() {
        // 20%, maxDiscount 30k, minOrder 100k
        VoucherRow voucher = samplePercentVoucher("DISCOUNT20", VoucherStatus.ACTIVE, 100, 5, 2);
        when(repository.findByCode("DISCOUNT20")).thenReturn(Optional.of(voucher));
        when(repository.countUserUsage(voucher.id(), 100L)).thenReturn(0);

        // Đơn 200.000: 20% = 40.000 -> trần maxDiscount 30.000 -> giảm 30.000
        VoucherValidateRequest req = new VoucherValidateRequest("DISCOUNT20", null, BigDecimal.valueOf(200000), BigDecimal.valueOf(15000));
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertTrue(res.valid());
        assertEquals(DiscountType.PERCENT, res.discountType());
        assertEquals(BigDecimal.valueOf(30000), res.discountAmount());
        assertEquals(BigDecimal.valueOf(170000), res.finalOrderAmount());
        assertEquals(BigDecimal.valueOf(15000), res.finalShippingFee());
    }

    @Test
    @DisplayName("Validate thành công và tính giảm giá LOẠI 2: FIXED (số tiền cố định)")
    void validateVoucher_FixedSuccess() {
        // Giảm 25.000đ, minOrder 50k
        VoucherRow voucher = sampleFixedVoucher("FIXED25K", BigDecimal.valueOf(25000), BigDecimal.valueOf(50000));
        when(repository.findByCode("FIXED25K")).thenReturn(Optional.of(voucher));
        when(repository.countUserUsage(voucher.id(), 100L)).thenReturn(0);

        // Đơn 100.000đ -> giảm 25.000đ -> còn 75.000đ
        VoucherValidateRequest req = new VoucherValidateRequest("FIXED25K", null, BigDecimal.valueOf(100000), BigDecimal.valueOf(18000));
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertTrue(res.valid());
        assertEquals(DiscountType.FIXED, res.discountType());
        assertEquals(BigDecimal.valueOf(25000), res.discountAmount());
        assertEquals(BigDecimal.valueOf(75000), res.finalOrderAmount());
    }

    @Test
    @DisplayName("Validate thành công và tính giảm giá LOẠI 3: FREE_SHIP (miễn phí vận chuyển)")
    void validateVoucher_FreeShipSuccess() {
        // Hỗ trợ ship 15.000đ, minOrder 50.000đ
        VoucherRow voucher = sampleFreeShipVoucher("FREESHIP15K", null);
        when(repository.findByCode("FREESHIP15K")).thenReturn(Optional.of(voucher));
        when(repository.countUserUsage(voucher.id(), 100L)).thenReturn(0);

        // Phí ship 20.000đ -> giảm 15.000đ -> phí ship còn lại 5.000đ
        VoucherValidateRequest req = new VoucherValidateRequest("FREESHIP15K", null, BigDecimal.valueOf(80000), BigDecimal.valueOf(20000));
        VoucherValidateResponse res = voucherService.validateVoucher(req, 100L);

        assertTrue(res.valid());
        assertEquals(DiscountType.FREE_SHIP, res.discountType());
        assertEquals(BigDecimal.valueOf(15000), res.discountAmount());
        assertEquals(BigDecimal.valueOf(80000), res.finalOrderAmount());
        assertEquals(BigDecimal.valueOf(5000), res.finalShippingFee());
    }

    // =========================================================================
    // 2. KIỂM THỬ QUYỀN ADMIN & MERCHANT (CRUD)
    // =========================================================================

    @Test
    @DisplayName("Admin tạo voucher toàn sàn thành công")
    void createAdminVoucher_Success() {
        AdminVoucherRequest req = new AdminVoucherRequest(
                "ADMINSALE50",
                "Siêu sale 50%",
                "Mô tả",
                DiscountType.PERCENT,
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(50000),
                now.plusDays(1),
                now.plusDays(10),
                500,
                1,
                null, // toàn sàn
                VoucherStatus.ACTIVE
        );

        when(repository.existsByCode("ADMINSALE50", null)).thenReturn(false);
        when(repository.create(any(Voucher.class))).thenReturn(10L);

        VoucherRow savedRow = new VoucherRow(
                10L, "ADMINSALE50", "Siêu sale 50%", "Mô tả", "PERCENT",
                BigDecimal.valueOf(50), BigDecimal.valueOf(100000), BigDecimal.valueOf(50000),
                now.plusDays(1), now.plusDays(10), 500, 1, 0, null, 1L, "ACTIVE", now
        );
        when(repository.findById(10L)).thenReturn(Optional.of(savedRow));

        VoucherResponse res = voucherService.createAdminVoucher(req, 1L);

        assertNotNull(res);
        assertEquals("ADMINSALE50", res.code());
        assertEquals(DiscountType.PERCENT, res.discountType());
        verify(repository, times(1)).create(any(Voucher.class));
    }

    @Test
    @DisplayName("Admin tạo voucher thất bại khi ngày kết thúc trước ngày bắt đầu")
    void createAdminVoucher_InvalidDates() {
        AdminVoucherRequest req = new AdminVoucherRequest(
                "BAD_DATES", "Tiêu đề", null, DiscountType.FIXED,
                BigDecimal.valueOf(10000), BigDecimal.ZERO, null,
                now.plusDays(5), now.plusDays(1), // endAt < startAt
                100, 1, null, VoucherStatus.ACTIVE
        );

        assertThrows(ConflictException.class, () -> voucherService.createAdminVoucher(req, 1L));
        verify(repository, never()).create(any(Voucher.class));
    }

    @Test
    @DisplayName("Merchant tạo voucher thành công cho quán thuộc quyền sở hữu")
    void createMerchantVoucher_Success() {
        Long merchantUserId = 5L;
        Long restaurantId = 12L;

        MerchantVoucherRequest req = new MerchantVoucherRequest(
                restaurantId,
                "QUAN12GIAM20K",
                "Quán 12 giảm 20k",
                null,
                DiscountType.FIXED,
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(60000),
                null,
                now.plusDays(1),
                now.plusDays(7),
                200,
                1,
                VoucherStatus.ACTIVE
        );

        when(repository.isRestaurantOwnedByUser(restaurantId, merchantUserId)).thenReturn(true);
        when(repository.existsByCode("QUAN12GIAM20K", null)).thenReturn(false);
        when(repository.create(any(Voucher.class))).thenReturn(20L);

        VoucherRow savedRow = new VoucherRow(
                20L, "QUAN12GIAM20K", "Quán 12 giảm 20k", null, "FIXED",
                BigDecimal.valueOf(20000), BigDecimal.valueOf(60000), null,
                now.plusDays(1), now.plusDays(7), 200, 1, 0, restaurantId, merchantUserId, "ACTIVE", now
        );
        when(repository.findById(20L)).thenReturn(Optional.of(savedRow));
        when(repository.getRestaurantNamesByIds(any())).thenReturn(Map.of(restaurantId, "Quán Cơm Tấm 12"));

        VoucherResponse res = voucherService.createMerchantVoucher(req, merchantUserId);

        assertNotNull(res);
        assertEquals("QUAN12GIAM20K", res.code());
        assertEquals("Quán Cơm Tấm 12", res.restaurantName());
    }

    @Test
    @DisplayName("Merchant tạo voucher thất bại khi cố tình tạo cho quán của người khác")
    void createMerchantVoucher_Forbidden_NotOwned() {
        Long merchantUserId = 5L;
        Long otherRestaurantId = 999L;

        MerchantVoucherRequest req = new MerchantVoucherRequest(
                otherRestaurantId,
                "HACK_CODE", "Tiêu đề", null, DiscountType.FIXED,
                BigDecimal.valueOf(10000), BigDecimal.ZERO, null,
                now.plusDays(1), now.plusDays(3), 10, 1, VoucherStatus.ACTIVE
        );

        when(repository.isRestaurantOwnedByUser(otherRestaurantId, merchantUserId)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> voucherService.createMerchantVoucher(req, merchantUserId));
        verify(repository, never()).create(any(Voucher.class));
    }

    // =========================================================================
    // 3. KIỂM THỬ GHI NHẬN SỬ DỤNG VOUCHER (TRANSACTION & ATOMIC LIMIT)
    // =========================================================================

    @Test
    @DisplayName("Ghi nhận sử dụng voucher thành công khi còn lượt")
    void recordVoucherUsage_Success() {
        Long voucherId = 1L;
        Long userId = 100L;
        Long orderId = 888L;

        when(repository.incrementUsedCount(voucherId)).thenReturn(1);

        assertDoesNotThrow(() -> voucherService.recordVoucherUsage(voucherId, userId, orderId));
        verify(repository, times(1)).incrementUsedCount(voucherId);
        verify(repository, times(1)).recordUsage(eq(voucherId), eq(userId), eq(orderId), any());
    }

    @Test
    @DisplayName("Ghi nhận sử dụng voucher thất bại khi đồng thời hết lượt (Atomic check = 0)")
    void recordVoucherUsage_LimitExceeded_ThrowsConflict() {
        Long voucherId = 1L;
        Long userId = 100L;
        Long orderId = 888L;

        // Giả lập 2 request đồng thời, request này bị từ chối do used_count >= usage_limit
        when(repository.incrementUsedCount(voucherId)).thenReturn(0);

        assertThrows(ConflictException.class, () -> voucherService.recordVoucherUsage(voucherId, userId, orderId));
        verify(repository, never()).recordUsage(any(), any(), any(), any());
    }
}

