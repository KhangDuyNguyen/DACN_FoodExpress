package vn.hutech.foodexpress.driver.data.location

/** Dependency-free checks, run with the cached Kotlin compiler; not a JUnit/Gradle test suite. */
object DriverLocationPolicyChecks {
    @JvmStatic fun main(args: Array<String>) {
        val now = 100_000_000_000L
        val fix = DriverLocationFix(10.0, 106.0, now, 1_700_000_000_000L, 10f)
        var checks = 0
        fun expect(value: DriverLocationFix, result: DriverGpsStatus?) {
            check(DriverLocationPolicy.problem(value, now) == result)
            checks++
        }
        expect(fix, null)
        expect(fix.copy(lat = 0.0, lng = 0.0), null)
        expect(fix.copy(lat = Double.NaN), DriverGpsStatus.ERROR)
        expect(fix.copy(lng = Double.POSITIVE_INFINITY), DriverGpsStatus.ERROR)
        expect(fix.copy(lat = 90.1), DriverGpsStatus.ERROR)
        expect(fix.copy(lng = -180.1), DriverGpsStatus.ERROR)
        expect(fix.copy(elapsedNanos = now - 15_000_000_000), null)
        expect(fix.copy(elapsedNanos = now - 15_000_000_001), DriverGpsStatus.STALE)
        expect(fix.copy(elapsedNanos = now + 1), DriverGpsStatus.STALE)
        expect(fix.copy(timeMillis = 0), DriverGpsStatus.STALE)
        expect(fix.copy(accuracyMeters = 50f), null)
        expect(fix.copy(accuracyMeters = 50.01f), DriverGpsStatus.INACCURATE)
        expect(fix.copy(accuracyMeters = null), DriverGpsStatus.INACCURATE)
        expect(fix.copy(accuracyMeters = Float.NaN), DriverGpsStatus.INACCURATE)
        expect(fix.copy(accuracyMeters = -1f), DriverGpsStatus.INACCURATE)
        println("PASS: $checks GPS policy assertions")
    }
}
