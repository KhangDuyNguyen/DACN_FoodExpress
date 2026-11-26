package vn.hutech.fooddelivery.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.cart.CartRepository;
import vn.hutech.fooddelivery.cart.CartRow;
import vn.hutech.fooddelivery.cart.OptionRules;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.dto.CreateOrderResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderPreviewRequest;
import vn.hutech.fooddelivery.order.dto.PriceBreakdown;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.payment.VnPayService;
import vn.hutech.fooddelivery.promotion.VoucherService;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateResponse;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private final ApplicationEventPublisher eventPublisher;
    /** Voucher cua Diep (package promotion): chi goi ham, khong ghi thang bang voucher. */
    private final VoucherService voucherService;
    /** Tao giao dich va URL thanh toan VNPay (package payment). */
    private final VnPayService vnPayService;

    // Chi 1 constructor: Spring tu dung no de tiem phu thuoc.
    // (Co them constructor thu 2 thi Spring khong biet chon cai nao, app bao "No default constructor found".)
    public OrderService(CartRepository cartRepository,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher,
                        VoucherService voucherService,
                        VnPayService vnPayService) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.voucherService = voucherService;
        this.vnPayService = vnPayService;
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
        // Khach nhap ma: xem truoc so tien giam. Ma khong dung duoc van tra 200, kem ly do de trang thanh toan hien
        if (hasText(req.voucherCode())) {
            VoucherValidateResponse voucher = checkVoucher(userId, restaurant.id(), price, req.voucherCode());
            price = voucher.valid()
                    ? OrderPricing.withVoucher(price, voucher.discountAmount(), voucher.code(), voucher.title(),
                            voucher.message())
                    : OrderPricing.voucherRejected(price, voucher.message());
        }
        return price;
    }

    /**
     * Dat hang tu gio cua khach. Tat ca nam trong MOT transaction:
     * loi o bat ky buoc nao thi rollback het (khong co don "nua voi", gio con nguyen).
     *
     * @param clientIp IP cua khach, VNPay yeu cau gui kem (vnp_IpAddr)
     */
    @Transactional
    public CreateOrderResponse createOrder(long userId, CreateOrderRequest req, String clientIp) {
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
            if (!line.itemAvailable()) {
                throw new ConflictException("Món \"" + line.name() + "\" đã hết, vui lòng xóa khỏi giỏ hàng");
            }
            // Quan co the doi tuy chon sau khi khach them vao gio (het, xoa han, doi nhom bat buoc):
            // kiem tra lai bang dung luat luc them vao gio
            Optional<String> optionProblem = OptionRules.findProblem(
                    cartRepository.findItemOptions(line.menuItemId()), line.optionIds());
            if (optionProblem.isPresent()) {
                throw new ConflictException("Món \"" + line.name() + "\": " + optionProblem.get()
                        + ". Hãy xóa món khỏi giỏ hàng và thêm lại");
            }
        }

        // 3. Tinh tien bang gia trong database (cung cong thuc voi xem truoc)
        PriceBreakdown price = OrderPricing.calculate(lines, restaurant.lat(), restaurant.lng(),
                req.deliveryLat(), req.deliveryLng(), findFeeRule());
        checkDistance(price);
        if (price.subtotal().compareTo(restaurant.minOrderAmount()) < 0) {
            throw new ConflictException("Đơn tối thiểu của quán là " + formatVnd(restaurant.minOrderAmount()));
        }

        // 4. Voucher (neu co): kiem tra lai ngay luc dat (ma co the vua het han/het luot sau khi xem truoc).
        //    Khong dung duoc -> 409 kem ly do, khong tao don, gio giu nguyen.
        VoucherValidateResponse voucher = null;
        if (hasText(req.voucherCode())) {
            voucher = checkVoucher(userId, restaurant.id(), price, req.voucherCode());
            if (!voucher.valid()) {
                throw new ConflictException(voucher.message());
            }
            price = OrderPricing.withVoucher(price, voucher.discountAmount(), voucher.code(), voucher.title(),
                    voucher.message());
        }

        // 5. Ghi don, tung mon, lich su trang thai.
        //    COD: quan xac nhan duoc ngay (PLACED). VNPay: cho khach tra tien (PENDING_PAYMENT), quan chua thay don.
        boolean payByVnPay = "VNPAY".equals(req.paymentMethod());
        String status = payByVnPay ? "PENDING_PAYMENT" : "PLACED";
        long orderId = insertOrderWithNewCode(userId, restaurant.id(), req, price, status);
        for (CartLine line : lines) {
            orderRepository.insertOrderItem(orderId, line);
        }
        orderRepository.insertStatusHistory(orderId, status, userId, null);

        // 6. Ghi luot dung voucher bang ham cua Diep (tang used_count co dieu kien + ghi voucher_usages).
        //    Can orderId nen phai sau buoc 5. Het luot giua chung -> 409, CA DON rollback (cung transaction).
        if (voucher != null) {
            voucherService.recordVoucherUsage(voucher.voucherId(), userId, orderId);
        }

        // 7. Dat xong thi xoa gio (cart_items tu xoa theo nho ON DELETE CASCADE). Don VNPay cung xoa:
        //    mon da nam trong don; thanh toan that bai thi don bi huy, khach dat lai.
        cartRepository.deleteCart(cart.id());
        OrderDetailResponse order = getDetail(orderId);

        // 8. VNPay: ghi giao dich PENDING va tao URL thanh toan (cung transaction nay).
        //    Khi VNPay bao da tra tien (IPN hoac truy van luc khach quay ve), PaymentResultService
        //    moi chuyen don sang PLACED va bao quan, moi tai xe.
        if (payByVnPay) {
            String paymentUrl = vnPayService.createPayment(orderId, order.code(), order.totalAmount(), clientIp);
            return new CreateOrderResponse(order, paymentUrl);
        }

        // 9. COD: bao cho module khac co don moi ngay: moi tai xe (OrderAssignmentListener),
        //    day len dashboard cua hang qua WebSocket (OrderEventListener cua Diep).
        //    Ben nghe dung AFTER_COMMIT nen chi chay khi ca transaction nay commit thanh cong.
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, restaurant.id(), order));
        return new CreateOrderResponse(order, null);
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
    private long insertOrderWithNewCode(long userId, long restaurantId, CreateOrderRequest req, PriceBreakdown price,
                                        String status) {
        for (int attempt = 1; ; attempt++) {
            String code = "FD" + LocalDate.now(VIETNAM).format(CODE_DATE)
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            NewOrder order = new NewOrder(code, userId, restaurantId,
                    req.receiverName().trim(), req.receiverPhone(), req.deliveryAddress().trim(),
                    req.deliveryLat(), req.deliveryLng(), cleanNote(req.note()),
                    price.subtotal(), price.deliveryFee(), price.discountAmount(), price.totalAmount(),
                    req.paymentMethod(), price.distanceKm(), status);
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

    /**
     * Hoi VoucherService (Diep): ma co dung duoc khong, giam bao nhieu.
     * Dua vao tam tinh va phi giao DO SERVER TINH (khong tin so tien client gui).
     */
    private VoucherValidateResponse checkVoucher(long userId, long restaurantId, PriceBreakdown price, String code) {
        return voucherService.validateVoucher(
                new VoucherValidateRequest(code.trim(), restaurantId, price.subtotal(), price.deliveryFee()), userId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    /** 30000 -> "30.000đ" */
    private static String formatVnd(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }
}
