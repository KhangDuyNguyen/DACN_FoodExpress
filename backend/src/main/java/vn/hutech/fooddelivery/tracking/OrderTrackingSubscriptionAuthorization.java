package vn.hutech.fooddelivery.tracking;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.hutech.fooddelivery.security.AuthUser;

import java.util.regex.Pattern;

@Service
public class OrderTrackingSubscriptionAuthorization {
    private static final Pattern LOCATION_TOPIC = Pattern.compile("/topic/orders/([1-9][0-9]{0,18})/location");

    private final OrderTrackingRepository trackingRepository;

    public OrderTrackingSubscriptionAuthorization(OrderTrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    /** The caller supplies the server-authenticated session identity. */
    public boolean canSubscribe(Authentication authentication, String destination) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthUser user)
                || !"CUSTOMER".equals(user.role()) || destination == null) {
            return false;
        }

        var topic = LOCATION_TOPIC.matcher(destination);
        if (!topic.matches()) {
            return false;
        }
        long orderId;
        try {
            orderId = Long.parseLong(topic.group(1));
        } catch (NumberFormatException exception) {
            return false;
        }
        return trackingRepository.isOwnedBy(orderId, user.id());
    }
}
