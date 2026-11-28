package vn.hutech.fooddelivery.tracking;

import java.time.Instant;
import java.util.List;

public record OrderTrackingRouteResponse(
        long orderId, String vehicleProfile, Geometry geometry, double distanceMeters, long durationMs,
        Origin origin, Instant generatedAt) {

    public record Geometry(String type, List<List<Double>> coordinates) {
    }

    public record Origin(long locationId, double lat, double lng, Instant recordedAt) {
    }
}
