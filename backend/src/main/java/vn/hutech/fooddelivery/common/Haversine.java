package vn.hutech.fooddelivery.common;

/** Khoang cach giua 2 toa do (km) theo cong thuc Haversine. Dung cho tinh phi ship va gan tai xe gan nhat. */
public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Haversine() {
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
