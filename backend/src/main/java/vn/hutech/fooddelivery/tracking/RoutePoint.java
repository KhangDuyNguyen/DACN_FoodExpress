package vn.hutech.fooddelivery.tracking;

record RoutePoint(double lat, double lng) {
    static RoutePoint from(Number lat, Number lng) {
        return lat == null || lng == null ? null : new RoutePoint(lat.doubleValue(), lng.doubleValue());
    }

    boolean isValid() {
        return Double.isFinite(lat) && Math.abs(lat) <= 90
                && Double.isFinite(lng) && Math.abs(lng) <= 180;
    }

    double distanceTo(RoutePoint other) {
        double latitudeDelta = Math.toRadians(other.lat - lat);
        double longitudeDelta = Math.toRadians(other.lng - lng);
        double sinLat = Math.sin(latitudeDelta / 2);
        double sinLng = Math.sin(longitudeDelta / 2);
        double a = sinLat * sinLat + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat))
                * sinLng * sinLng;
        return 12_742_000 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    String asVietmapPoint() {
        return lat + "," + lng;
    }
}
