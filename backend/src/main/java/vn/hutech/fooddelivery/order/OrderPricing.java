package vn.hutech.fooddelivery.order;

import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.common.Haversine;
import vn.hutech.fooddelivery.order.dto.PriceBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cac phep tinh tien cua don hang. Chi tinh toan, khong doc database,
 * nen test rieng duoc bang unit test (OrderPricingTest).
 * Tien dung BigDecimal (khong dung double de khong bi sai so lam tron).
 */
public final class OrderPricing {

    private OrderPricing() {
    }

    /** Tam tinh = tong (don gia x so luong) cua moi dong. Don gia lay tu database, khong lay tu client. */
    public static BigDecimal subtotal(List<CartLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CartLine line : lines) {
            sum = sum.add(line.lineTotal());
        }
        return sum;
    }

    /** Khoang cach tu quan den diem giao (km), lam tron 2 so le cho khop cot orders.distance_km DECIMAL(6,2). */
    public static BigDecimal distanceKm(double fromLat, double fromLng, double toLat, double toLng) {
        return BigDecimal.valueOf(Haversine.distanceKm(fromLat, fromLng, toLat, toLng))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Phi giao = base_fee cho base_distance_km dau tien, moi km tiep theo cong per_km_fee.
     * Phan km le lam tron LEN thanh 1 km.
     * Voi 15.000d cho 2 km dau, 5.000d/km: 1,8 km -> 15.000d; 2,1 km -> 20.000d; 5 km -> 30.000d.
     */
    public static BigDecimal deliveryFee(BigDecimal distanceKm, DeliveryFeeRule rule) {
        BigDecimal extraKm = distanceKm.subtract(rule.baseDistanceKm());
        if (extraKm.signum() <= 0) {
            return rule.baseFee();
        }
        BigDecimal extraKmRoundedUp = extraKm.setScale(0, RoundingMode.CEILING);
        return rule.baseFee().add(rule.perKmFee().multiply(extraKmRoundedUp));
    }

    /** Tong = tam tinh + phi giao - giam gia, khong bao gio am. */
    public static BigDecimal total(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal discount) {
        BigDecimal total = subtotal.add(deliveryFee).subtract(discount);
        return total.signum() < 0 ? BigDecimal.ZERO : total;
    }

    /** Gop cac buoc tren. "Xem truoc" va "dat hang" cung goi ham nay nen so tien luon giong nhau. */
    public static PriceBreakdown calculate(List<CartLine> lines,
                                           double restaurantLat, double restaurantLng,
                                           double deliveryLat, double deliveryLng,
                                           DeliveryFeeRule rule) {
        BigDecimal subtotal = subtotal(lines);
        BigDecimal distance = distanceKm(restaurantLat, restaurantLng, deliveryLat, deliveryLng);
        BigDecimal fee = deliveryFee(distance, rule);
        // Chua co voucher: giam 0. Voucher ap sau bang withVoucher(...)
        return new PriceBreakdown(subtotal, fee, BigDecimal.ZERO, total(subtotal, fee, BigDecimal.ZERO), distance,
                null, null, null);
    }

    /**
     * Ap so tien giam cua voucher vao bang tien, tinh lai tong.
     * So tien giam do VoucherService (Diep) tinh tu tam tinh va phi giao CUA SERVER, khong lay tu client.
     */
    public static PriceBreakdown withVoucher(PriceBreakdown price, BigDecimal discount,
                                             String code, String title, String message) {
        // Tien VND khong co so le (cot DECIMAL(12,0)); gia tri voucher luu DECIMAL(12,2) nen lam tron ve 0 so le
        BigDecimal roundedDiscount = discount.setScale(0, RoundingMode.HALF_UP);
        return new PriceBreakdown(price.subtotal(), price.deliveryFee(), roundedDiscount,
                total(price.subtotal(), price.deliveryFee(), roundedDiscount), price.distanceKm(), code, title, message);
    }

    /** Khach nhap ma nhung ma khong dung duoc: giu nguyen tien, chi ghi ly do. */
    public static PriceBreakdown voucherRejected(PriceBreakdown price, String reason) {
        return new PriceBreakdown(price.subtotal(), price.deliveryFee(), price.discountAmount(), price.totalAmount(),
                price.distanceKm(), null, null, reason);
    }
}
