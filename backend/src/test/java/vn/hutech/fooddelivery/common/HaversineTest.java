package vn.hutech.fooddelivery.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaversineTest {

    @Test
    void samePointIsZero() {
        assertEquals(0.0, Haversine.distanceKm(10.7769, 106.7009, 10.7769, 106.7009), 1e-9);
    }

    @Test
    void oneDegreeOfLongitudeAtEquatorIsAbout111Km() {
        assertEquals(111.19, Haversine.distanceKm(0, 0, 0, 1), 0.2);
    }
}
