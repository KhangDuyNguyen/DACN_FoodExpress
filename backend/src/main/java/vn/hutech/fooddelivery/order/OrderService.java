package vn.hutech.fooddelivery.order;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.cart.CartRepository;
import vn.hutech.fooddelivery.cart.CartRow;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderPreviewRequest;
import vn.hutech.fooddelivery.order.dto.PriceBreakdown;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Nghiep vu dat hang: xem truoc tien va tao don tu gio hang. */
@Service
public class OrderService {

    static final String EMPTY_CART = "Giỏ hàng trống";
    /** Xa hon muc nay thi khong giao (cung tranh vuot cot distance_km DECIMAL(6,2)). */
    static final BigDecimal MAX_DELIVERY_KM = new BigDecimal("30");
    private static final int MAX_CODE_ATTEMPTS = 3;
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;

    public OrderService(CartRepository cartRepository, OrderRepository orderRepository) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Xem truoc tien cua gio hien tai den diem giao (de trang thanh toan hien phi ship, tong tien).
     * Chi tinh, khong ghi gi. Viec kiem tra mon het, quan dong, don toi thieu lam lai khi dat hang that.
     */
    @Transactional(readOnly = true)
    public PriceBreakdown preview(long userId, OrderPreviewRequest req) {
        CartRow cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ConflictException(EMPTY_CART));
        List<CartLine> lines = cartRepository.findLines(cart.id());
        if (lines.isEmpty()) {
            throw new ConflictException(EMPTY_CART);
        }
        RestaurantForOrder restaurant = findRestaurant(cart.restaurantId());
        PriceBreakdown price = OrderPricing.calculate(lines, restaurant.lat(), restaurant.lng(),
                req.deliveryLat(), req.deliveryLng(), findFeeRule());
        checkDistance(price);
        return price;
    }

    /**
     * Dat hang tu gio cua khach. Tat ca nam trong MOT transaction:
     * loi o bat ky buoc nao thi rollback het (khong co don "nua voi", gio con nguyen).
     */
    @Transactional
    public OrderDetailResponse createOrder(long userId, CreateOrderRequest req) {
        // 1. Khoa dong gio den het transaction. Bam "Dat hang" 2 lan thi lan 2 phai cho lan 1 xong;
        //    luc do gio da bi xoa nen lan 2 nhan 409, khong tao ra 2 don.
        CartRow cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ConflictException(EMPTY_CART));
        List<CartLine> lines = cartRepository.findLines(cart.id());
        if (lines.isEmpty()) {
            throw new ConflictException(EMPTY_CART);
        }

        // 2. Kiem tra lai quan va tung mon theo du lieu MOI NHAT trong database
        RestaurantForOrder restaurant = findRestaurant(cart.restaurantId());
        if (!restaurant.canOrder()) {
            throw new ConflictException("Quán đang đóng cửa, chưa đặt được");
        }
        for (CartLine line : lines) {
            if (!line.available()) {
                throw new ConflictException("Món \"" + line.name() + "\" đã hết, vui lòng xóa khỏi giỏ hàng");
            }
        }

        // 3. Tinh tien bang gia trong database (cung cong thuc voi xem truoc)
        PriceBreakdown price = OrderPricing.calculate(lines, restaurant.lat(), restaurant.lng(),
                req.deliveryLat(), req.deliveryLng(), findFeeRule());
        checkDistance(price);
        if (price.subtotal().compareTo(restaurant.minOrderAmount()) < 0) {
            throw new ConflictException("Đơn tối thiểu của quán là " + formatVnd(restaurant.minOrderAmount()));
        }

        // 4. Ghi don, tung mon, lich su trang thai
        long orderId = insertOrderWithNewCode(userId, restaurant.id(), req, price);
        for (CartLine line : lines) {
            orderRepository.insertOrderItem(orderId, line);
        }
        orderRepository.insertStatusHistory(orderId, "PLACED", userId, null);

        // 5. Dat xong thi xoa gio (cart_items tu xoa theo nho ON DELETE CASCADE)
        cartRepository.deleteCart(cart.id());

        return getDetail(orderId);
    }

    /** Chi tiet don theo id (Module D dung lai, kem kiem tra chu don). */
    public OrderDetailResponse getDetail(long orderId) {
        OrderRow row = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        return OrderDetailResponse.from(row, orderRepository.findItems(orderId), orderRepository.findHistory(orderId));
    }

    /**
     * Ma don = "FD" + ngay (yyMMdd) + 6 so ngau nhien, vi du FD261107042195.
     * Rat hiem khi trung; neu trung thi cot code UNIQUE bao loi, sinh ma khac va thu lai (toi da 3 lan).
     */
    private long insertOrderWithNewCode(long userId, long restaurantId, CreateOrderRequest req, PriceBreakdown price) {
        for (int attempt = 1; ; attempt++) {
            String code = "FD" + LocalDate.now(VIETNAM).format(CODE_DATE)
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            NewOrder order = new NewOrder(code, userId, restaurantId,
                    req.receiverName().trim(), req.receiverPhone(), req.deliveryAddress().trim(),
                    req.deliveryLat(), req.deliveryLng(), cleanNote(req.note()),
                    price.subtotal(), price.deliveryFee(), price.discountAmount(), price.totalAmount(),
                    req.paymentMethod(), price.distanceKm());
            try {
                return orderRepository.insertOrder(order);
            } catch (DuplicateKeyException ex) {
                if (attempt == MAX_CODE_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    private RestaurantForOrder findRestaurant(long restaurantId) {
        return orderRepository.findRestaurant(restaurantId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quán"));
    }

    private DeliveryFeeRule findFeeRule() {
        return orderRepository.findActiveFeeRule()
                .orElseThrow(() -> new ConflictException("Chưa cấu hình phí giao hàng"));
    }

    private static void checkDistance(PriceBreakdown price) {
        if (price.distanceKm().compareTo(MAX_DELIVERY_KM) > 0) {
            throw new ConflictException("Địa chỉ giao quá xa quán (tối đa " + MAX_DELIVERY_KM + " km)");
        }
    }

    private static String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    /** 30000 -> "30.000đ" */
    private static String formatVnd(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }
}
