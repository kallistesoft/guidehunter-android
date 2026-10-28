package ca.kallistesoft.guidehunter.core.model

import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The current time: when a boost ends. A fixed instant in tests. */
fun interface Now {
    operator fun invoke(): Instant
}

/**
 * A boost puts a live offer first on the map for [DURATION], after its guide watches an ad. Boosts don't stack:
 * boosting again restarts the [DURATION] from now, so only guides who come back stay featured.
 */
object Boost {
    val DURATION = 3.days

    /** "Boost again" shows once less than this is left (or the boost has ended). */
    val RENEW_WITHIN = 1.days

    /** At most this many boosted offers are featured at a time; when more are in view, they take turns. */
    const val MAX_FEATURED = 3

    /** When a boost started [now] ends. */
    fun until(now: Instant): Instant = now + DURATION

    /** Whether the guide may boost an offer boosted until [boostedUntil] (null: never) again. */
    fun canBoost(boostedUntil: Instant?, now: Instant): Boolean = boostedUntil == null || boostedUntil - now < RENEW_WITHIN

    /**
     * The [boosted] guides to feature: all of them up to [MAX_FEATURED], otherwise [MAX_FEATURED] picked at random.
     * Each guide's chance comes from [seed] and its id alone, not from the others: with the same seed, a featured guide
     * stays featured while the map pans, as long as no luckier one comes into view. Luckiest first.
     */
    fun featured(boosted: List<Guide>, seed: Long): List<Guide> =
        if (boosted.size <= MAX_FEATURED) boosted else boosted.sortedBy { Random(seed xor it.id.hashCode().toLong()).nextLong() }.take(MAX_FEATURED)
}
