package vn.hutech.fooddelivery.tracking;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.user.UserRepository;

import java.util.regex.Pattern;

@Service
class MerchantOrderSubscriptionAuthorization {
    private static final Pattern RESTAURANT_ORDERS =
            Pattern.compile("/topic/restaurants/([1-9][0-9]{0,18})/orders");

    private final OrderRepository orders;
    private final UserRepository users;

    MerchantOrderSubscriptionAuthorization(OrderRepository orders, UserRepository users) {
        this.orders = orders;
        this.users = users;
    }

    boolean canSubscribe(Authentication authentication, String destination) {
        if (!authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof AuthUser user)
                || !"MERCHANT".equals(user.role()) || destination == null) {
            return false;
        }
        var topic = RESTAURANT_ORDERS.matcher(destination);
        if (!topic.matches()) {
            return false;
        }
        long restaurantId;
        try {
            restaurantId = Long.parseLong(topic.group(1));
        } catch (NumberFormatException ignored) {
            return false;
        }
        return users.findById(user.id())
                .filter(account -> !account.isBlocked() && "MERCHANT".equals(account.role()))
                .isPresent() && orders.isRestaurantOwnedBy(restaurantId, user.id());
    }
}
