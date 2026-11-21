package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.hutech.fooddelivery.security.AuthUser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTrackingSubscriptionAuthorizationTest {
    private static final long OWNER = 41;
    private static final long ORDER = 73;
    private static final String TOPIC = "/topic/orders/73/location";

    @Mock
    OrderTrackingRepository trackingRepository;

    @InjectMocks
    OrderTrackingSubscriptionAuthorization authorization;

    @Test
    void authenticatedCustomerCanSubscribeToTheirOrder() {
        when(trackingRepository.isOwnedBy(ORDER, OWNER)).thenReturn(true);

        assertTrue(authorization.canSubscribe(authenticated(OWNER, "CUSTOMER"), TOPIC));
    }

    @Test
    void anotherCustomerCannotSubscribe() {
        long otherCustomer = 42;
        when(trackingRepository.isOwnedBy(ORDER, otherCustomer)).thenReturn(false);

        assertFalse(authorization.canSubscribe(authenticated(otherCustomer, "CUSTOMER"), TOPIC));
        verify(trackingRepository).isOwnedBy(ORDER, otherCustomer);
    }

    @Test
    void missingOrUnauthenticatedIdentityIsRejected() {
        var unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(
                new AuthUser(OWNER, "CUSTOMER"), null);

        assertFalse(authorization.canSubscribe(null, TOPIC));
        assertFalse(authorization.canSubscribe(unauthenticated, TOPIC));
        verifyNoInteractions(trackingRepository);
    }

    @Test
    void principalWithOnlyANameIsRejected() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(Long.toString(OWNER), null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        assertFalse(authorization.canSubscribe(authentication, TOPIC));
        verifyNoInteractions(trackingRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRIVER", "MERCHANT", "ADMIN"})
    void otherRolesAreRejected(String role) {
        assertFalse(authorization.canSubscribe(authenticated(OWNER, role), TOPIC));
        verifyNoInteractions(trackingRepository);
    }

    @Test
    void missingOrderIsRejected() {
        when(trackingRepository.isOwnedBy(999, OWNER)).thenReturn(false);

        assertFalse(authorization.canSubscribe(authenticated(OWNER, "CUSTOMER"), "/topic/orders/999/location"));
        verify(trackingRepository).isOwnedBy(999, OWNER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/**", "/topic/orders/**", "/topic/orders/*/location",
            "/topic/orders/{id}/location", "/topic/orders/073/location", "/topic/orders/0/location",
            "/topic/orders/73/location/extra", "/topic/orders/9223372036854775808/location"})
    void wildcardAndNonCanonicalTopicsAreRejected(String destination) {
        assertFalse(authorization.canSubscribe(authenticated(OWNER, "CUSTOMER"), destination));
        verifyNoInteractions(trackingRepository);
    }

    private Authentication authenticated(long userId, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(new AuthUser(userId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
