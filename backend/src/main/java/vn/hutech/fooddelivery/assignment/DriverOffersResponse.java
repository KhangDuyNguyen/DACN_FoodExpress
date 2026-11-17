package vn.hutech.fooddelivery.assignment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Gio Backend cung mui gio voi offeredAt/expiresAt; khong dung dong ho client de quyet dinh het han. */
public record DriverOffersResponse(List<Offer> offers, LocalDateTime serverTime) {
    /** Khong chua ten, so dien thoai, ghi chu hay ID tai khoan cua khach. */
    @Schema(name = "DriverOfferSummary")
    public record Offer(long offerId, long orderId, String orderCode,
                        String restaurantName, String restaurantAddress,
                        BigDecimal pickupLat, BigDecimal pickupLng, String deliveryAddress,
                        BigDecimal distanceKm, LocalDateTime offeredAt, LocalDateTime expiresAt) {
    }
}
