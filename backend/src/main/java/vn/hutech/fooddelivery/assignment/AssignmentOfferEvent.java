package vn.hutech.fooddelivery.assignment;

import java.time.Instant;

/** Published inside the transaction that creates or accepts an offer. */
public record AssignmentOfferEvent(Kind kind, long offerId, long orderId, long restaurantId,
                                   long driverId, Instant expiresAt) {
    public enum Kind { CREATED, ACCEPTED }
}
