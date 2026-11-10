package vn.hutech.fooddelivery.cart;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.cart.CartRepository.CartItemRow;
import vn.hutech.fooddelivery.cart.CartRepository.CartRestaurant;
import vn.hutech.fooddelivery.cart.dto.AddCartItemRequest;
import vn.hutech.fooddelivery.cart.dto.CartItemResponse;
import vn.hutech.fooddelivery.cart.dto.CartResponse;
import vn.hutech.fooddelivery.cart.dto.UpdateCartItemRequest;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.order.OrderPricing;

import java.util.List;
import java.util.Optional;

/**
 * Nghiep vu gio hang. Moi khach co toi da 1 gio, va 1 gio chi chua mon cua 1 quan.
 * userId luon lay tu token (controller), nen khach chi doc/sua duoc gio cua chinh minh.
 */
@Service
public class CartService {

    static final int MAX_QUANTITY = 99;

    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElse(CartResponse.empty());
    }

    @Transactional
    public CartResponse addItem(long userId, AddCartItemRequest req) {
        MenuItemInfo item = cartRepository.findMenuItemInfo(req.menuItemId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy món"));
        if (!item.restaurantCanOrder()) {
            throw new ConflictException("Quán đang đóng cửa, chưa đặt món được");
        }
        if (!item.available() || !item.categoryActive()) {
            throw new ConflictException("Món \"" + item.name() + "\" đã hết");
        }
        String note = cleanNote(req.note());

        // Khoa gio cua khach den het transaction; chua co gio thi tao moi gan voi quan cua mon nay
        Optional<CartRow> existing = cartRepository.findByUserIdForUpdate(userId);
        long cartId;
        if (existing.isEmpty()) {
            cartId = cartRepository.insertCart(userId, item.restaurantId());
        } else {
            cartId = existing.get().id();
            if (existing.get().restaurantId() != item.restaurantId()) {
                if (!req.replaceCart()) {
                    throw new ConflictException("Giỏ hàng đang có món của quán khác");
                }
                // Khach dong y bo gio cu: xoa het mon cu, chuyen gio sang quan moi
                cartRepository.deleteAllItems(cartId);
                cartRepository.updateRestaurant(cartId, item.restaurantId());
            }
        }

        // Cung mon, cung ghi chu thi cong don so luong; khac thi them dong moi
        Optional<CartItemRow> same = cartRepository.findSameItem(cartId, item.id(), note);
        if (same.isPresent()) {
            int newQuantity = same.get().quantity() + req.quantity();
            if (newQuantity > MAX_QUANTITY) {
                throw new ConflictException("Mỗi món tối đa " + MAX_QUANTITY + " phần");
            }
            cartRepository.updateQuantity(same.get().id(), newQuantity);
        } else {
            cartRepository.insertItem(cartId, item.id(), req.quantity(), note);
        }
        return getCart(userId);
    }

    @Transactional
    public CartResponse updateItem(long userId, long cartItemId, UpdateCartItemRequest req) {
        int updated = cartRepository.updateItemOfUser(userId, cartItemId, req.quantity(), cleanNote(req.note()));
        if (updated == 0) {
            // Khong co dong nay, hoac dong nay nam trong gio cua nguoi khac
            throw new NotFoundException("Không tìm thấy món trong giỏ hàng");
        }
        return getCart(userId);
    }

    @Transactional
    public CartResponse removeItem(long userId, long cartItemId) {
        int deleted = cartRepository.deleteItemOfUser(userId, cartItemId);
        if (deleted == 0) {
            throw new NotFoundException("Không tìm thấy món trong giỏ hàng");
        }
        // Xoa mon cuoi cung thi xoa luon gio, lan sau them mon cua quan nao cung duoc
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            if (cartRepository.countItems(cart.id()) == 0) {
                cartRepository.deleteCart(cart.id());
            }
        });
        return getCart(userId);
    }

    @Transactional
    public void clearCart(long userId) {
        cartRepository.deleteByUserId(userId);
    }

    private CartResponse toResponse(CartRow cart) {
        List<CartLine> lines = cartRepository.findLines(cart.id());
        CartRestaurant restaurant = cartRepository.findRestaurant(cart.restaurantId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quán của giỏ hàng"));
        int itemCount = lines.stream().mapToInt(CartLine::quantity).sum();
        return new CartResponse(
                restaurant.id(),
                restaurant.name(),
                restaurant.minOrderAmount(),
                lines.stream().map(CartItemResponse::from).toList(),
                itemCount,
                OrderPricing.subtotal(lines)
        );
    }

    /** Ghi chu toan dau cach -> null, de "" va null duoc coi la cung mot ghi chu khi cong don. */
    private static String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
