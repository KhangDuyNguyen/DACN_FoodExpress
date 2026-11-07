package vn.hutech.foodexpress.driver.ui.offer

import java.time.Duration
import java.time.LocalDateTime

/** LocalDateTime values share the Backend timezone; the device wall clock is never consulted. */
data class OfferDeadline(val elapsedDeadlineMillis: Long) {
    fun remainingSeconds(elapsedNowMillis: Long): Int =
        ((elapsedDeadlineMillis - elapsedNowMillis).coerceAtLeast(0).coerceAtMost(30_000) + 999).div(1_000).toInt()

    companion object {
        fun from(serverTime: LocalDateTime, expiresAt: LocalDateTime, requestStartedMillis: Long): OfferDeadline {
            // Subtract the entire request duration conservatively, including response transit time.
            val duration = Duration.between(serverTime, expiresAt)
            val remaining = when {
                duration.isNegative -> 0L
                duration >= Duration.ofSeconds(30) -> 30_000L
                else -> duration.toMillis()
            }
            return OfferDeadline(Math.addExact(requestStartedMillis, remaining))
        }
    }
}
