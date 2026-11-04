package vn.hutech.fooddelivery.assignment;

import vn.hutech.fooddelivery.common.Haversine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Thuat toan doc lap voi Spring/database, khong thay doi trang thai tai xe hay don hang. */
public final class NearestDriverSelector {

    // Nguong de xuat dua tren Online Tuan 3, chua la chinh sach Assignment da chot.
    private static final Duration MAX_GPS_AGE = Duration.ofSeconds(60);
    private static final Comparator<RankedDriver> RANKING = Comparator
            .comparingDouble(RankedDriver::distanceKm)
            .thenComparingLong(RankedDriver::driverId);

    /**
     * Tra danh sach bat bien tu gan den xa; bo qua ung vien null/khong du dieu kien.
     * now la thoi gian Backend do ben goi truyen vao, cung mui gio voi lastLocationAt.
     *
     * @throws IllegalArgumentException neu toa do nha hang khong hop le
     * @throws NullPointerException neu candidates hoac now la null
     */
    public List<RankedDriver> rank(double restaurantLat, double restaurantLng,
                                   List<DriverCandidate> candidates, LocalDateTime now) {
        if (!validCoordinate(restaurantLat, 90) || !validCoordinate(restaurantLng, 180)) {
            throw new IllegalArgumentException("Toa do nha hang khong hop le");
        }
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(now, "now");

        List<RankedDriver> ranked = new ArrayList<>();
        for (DriverCandidate candidate : candidates) {
            if (!isEligible(candidate, now)) {
                continue;
            }
            double distanceKm = Haversine.distanceKm(
                    candidate.lat(), candidate.lng(), restaurantLat, restaurantLng);
            // Khong de ket qua phi huu han tu sai so so thuc cua Haversine lot vao bang xep hang.
            if (Double.isFinite(distanceKm)) {
                ranked.add(new RankedDriver(candidate.driverId(), distanceKm));
            }
        }
        ranked.sort(RANKING);
        return List.copyOf(ranked);
    }

    /** Cung quy tac loc va xep hang voi rank; rong neu khong con tai xe hop le. */
    public Optional<RankedDriver> selectNearest(double restaurantLat, double restaurantLng,
                                               List<DriverCandidate> candidates, LocalDateTime now) {
        return rank(restaurantLat, restaurantLng, candidates, now).stream().findFirst();
    }

    private static boolean isEligible(DriverCandidate candidate, LocalDateTime now) {
        if (candidate == null || !"APPROVED".equals(candidate.status()) || !candidate.isOnline()
                || candidate.hasActiveOrder() || candidate.hasPendingOffer()
                || !validCoordinate(candidate.lat(), 90) || !validCoordinate(candidate.lng(), 180)
                || candidate.lastLocationAt() == null || candidate.lastLocationAt().isAfter(now)) {
            return false;
        }
        // So sanh day du Duration: dung 60 giay duoc nhan, 60 giay + 1 ns bi loai.
        return Duration.between(candidate.lastLocationAt(), now).compareTo(MAX_GPS_AGE) <= 0;
    }

    private static boolean validCoordinate(Double value, int limit) {
        return value != null && Double.isFinite(value) && value >= -limit && value <= limit;
    }
}
