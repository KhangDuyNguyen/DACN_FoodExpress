package vn.hutech.fooddelivery.tracking;

import java.util.ArrayList;
import java.util.List;

import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.ROUTE_UPSTREAM_ERROR;

final class RoutePolyline {
    private static final int MAX_POINTS = 20_000;
    private static final double MAX_ENDPOINT_ERROR_METERS = 150;
    private final String encoded;
    private int cursor;

    private RoutePolyline(String encoded) {
        this.encoded = encoded;
    }

    static OrderTrackingRouteResponse.Geometry decode(String encoded, RoutePoint origin, RoutePoint destination) {
        var decoder = new RoutePolyline(encoded);
        var coordinates = new ArrayList<List<Double>>();
        RoutePoint first = null;
        RoutePoint last = null;
        boolean hasMovement = false;
        long lat = 0;
        long lng = 0;
        while (decoder.cursor < encoded.length()) {
            if (coordinates.size() >= MAX_POINTS) {
                throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
            }
            lat += decoder.readDelta();
            lng += decoder.readDelta();
            var point = new RoutePoint(lat / 100_000.0, lng / 100_000.0);
            if (!point.isValid()) {
                throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
            }
            if (first == null) {
                first = point;
            }
            hasMovement |= !point.equals(first);
            last = point;
            coordinates.add(List.of(point.lng(), point.lat()));
        }

        if (!hasMovement || first.distanceTo(origin) > MAX_ENDPOINT_ERROR_METERS
                || last.distanceTo(destination) > MAX_ENDPOINT_ERROR_METERS) {
            throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        }
        return new OrderTrackingRouteResponse.Geometry("LineString", List.copyOf(coordinates));
    }

    private long readDelta() {
        long value = 0;
        for (int shift = 0; shift <= 30 && cursor < encoded.length(); shift += 5) {
            int chunk = encoded.charAt(cursor++) - 63;
            if (chunk < 0 || chunk > 63) {
                break;
            }
            value |= (long) (chunk & 31) << shift;
            if (chunk < 32) {
                return (value & 1) == 0 ? value >> 1 : ~(value >> 1);
            }
        }
        throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
    }
}
