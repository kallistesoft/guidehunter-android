package ca.kallistesoft.guidehunter.core.model

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")

    private fun guide(id: String, boostedUntil: Instant? = null) =
        Guide(id, "Trip $id", "", GuideActivity.HUNT, emptyList(), "", GeoPoint(46.0, -72.0), boostedUntil = boostedUntil)

    @Test
    fun `a boost lasts 3 days from now`() {
        assertEquals(Instant.parse("2026-10-31T12:00:00Z"), Boost.until(now))
    }

    @Test
    fun `a guide or an offer is boosted until the boost ends`() {
        assertFalse(guide("a").isBoosted(now))
        assertTrue(guide("a", now + 1.hours).isBoosted(now))
        assertFalse("ended", guide("a", now).isBoosted(now))
        assertFalse("ended", guide("a", now - 1.days).isBoosted(now))

        val offer = GuideOffer("a", OfferDraft(), live = true)
        assertFalse(offer.isBoosted(now))
        assertTrue(offer.copy(boostedUntil = now + 2.days).isBoosted(now))
        assertFalse(offer.copy(boostedUntil = now - 2.days).isBoosted(now))
    }

    @Test
    fun `boost again only when less than a day is left, or once it has ended`() {
        assertTrue("never boosted", Boost.canBoost(null, now))
        assertFalse(Boost.canBoost(now + 3.days, now))
        assertFalse(Boost.canBoost(now + 1.days, now))
        assertTrue(Boost.canBoost(now + 23.hours, now))
        assertTrue("ended", Boost.canBoost(now - 1.days, now))
    }

    @Test
    fun `up to 3 boosted guides are all featured`() {
        val three = listOf(guide("a"), guide("b"), guide("c"))
        assertEquals(three, Boost.featured(three, seed = 1))
        assertEquals(three.take(1), Boost.featured(three.take(1), seed = 1))
    }

    @Test
    fun `more than 3 boosted, 3 are picked, the same ones for the same seed`() {
        val boosted = ('a'..'j').map { guide(it.toString()) }
        val picked = Boost.featured(boosted, seed = 42)
        assertEquals(Boost.MAX_FEATURED, picked.size)
        assertTrue(boosted.containsAll(picked))
        assertEquals(picked, Boost.featured(boosted.shuffled(), seed = 42))
        // Another seed (another map) gives others a turn.
        val picks = (1L..20L).map { Boost.featured(boosted, it).map(Guide::id).toSet() }.toSet()
        assertTrue(picks.size > 1)
    }

    @Test
    fun `a featured guide stays featured while panning, until a luckier one comes into view`() {
        val boosted = ('a'..'j').map { guide(it.toString()) }
        val picked = Boost.featured(boosted, seed = 7)
        // Pan: some boosted guides leave the view, the picked ones stay.
        val others = boosted - picked.toSet()
        assertEquals(picked, Boost.featured(picked + others.take(2), seed = 7))
    }
}
