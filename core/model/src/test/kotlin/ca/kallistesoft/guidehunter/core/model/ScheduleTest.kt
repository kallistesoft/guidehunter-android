package ca.kallistesoft.guidehunter.core.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    private fun day(month: Int, day: Int, year: Int = 2027) = LocalDate(year, month, day)
    private val today = day(4, 15)

    /** The spring bear hunt: a week, leaving every Saturday from May 1 to June 26. */
    private val bear = Schedule(
        length = TripLength.Days(7),
        departures = generateSequence(day(5, 1)) { LocalDate.fromEpochDays(it.toEpochDays() + 7) }
            .takeWhile { it <= day(6, 26) }
            .map { Departure(it) }
            .toList(),
    )

    /** Salmon on Lake Ontario, by the half day. */
    private val salmon = Schedule(
        length = TripLength.HalfDay,
        departures = listOf(Departure(day(5, 20), Slot.AFTERNOON), Departure(day(5, 20), Slot.MORNING)),
    )

    @Test
    fun `a trip fits the dates when it starts and ends within them`() {
        assertTrue(bear.fits(Trip(day(5, 18), day(5, 31)), today))
        assertFalse("July 2: the season is over", bear.fits(Trip(day(7, 2)), today))
        assertFalse("a week doesn't fit in three days", bear.fits(Trip(day(5, 1), day(5, 3)), today))
    }

    @Test
    fun `upcoming trips come soonest first, with their end`() {
        val trips = bear.trips(day(5, 10))
        assertEquals(Trip(day(5, 15), day(5, 21)), trips.first())
        assertEquals(Trip(day(6, 26), day(7, 2)), trips.last())
    }

    @Test
    fun `dates only expire after the last departure`() {
        assertFalse(bear.expired(day(6, 26)))
        assertTrue(bear.expired(day(6, 27)))
        assertTrue(bear.trips(day(6, 27)).isEmpty())
        assertFalse("no dates: on request, never expired", Schedule(TripLength.Days(1)).expired(today))
        assertTrue(Schedule().onRequest)
    }

    @Test
    fun `every year, the same days come back`() {
        val yearly = bear.copy(everyYear = true)
        assertFalse(yearly.expired(day(1, 1, 2030)))
        assertTrue(yearly.fits(Trip(day(5, 18, 2030), day(5, 31, 2030)), day(1, 1, 2030)))
        assertEquals(day(5, 1, 2028), yearly.trips(day(7, 1)).first().start)
    }

    @Test
    fun `every year, a trip over New Year and February 29`() {
        val winter = Schedule(TripLength.Days(5), listOf(Departure(day(12, 30, 2026))), everyYear = true)
        assertTrue(winter.fits(Trip(day(12, 28, 2027), day(1, 5, 2028)), today))
        val leap = Schedule(TripLength.Days(1), listOf(Departure(day(2, 29, 2028))), everyYear = true)
        assertEquals(day(2, 28, 2029), leap.trips(day(3, 1, 2028)).first().start)
        assertEquals(day(2, 29, 2032), day(2, 29, 2028).inYear(2032))
    }

    @Test
    fun `half days come morning first, and both fit the day`() {
        assertEquals(listOf(Slot.MORNING, Slot.AFTERNOON), salmon.trips(today).map { it.slot })
        assertEquals(day(5, 20), salmon.trips(today).first().end)
        assertTrue(salmon.fits(Trip(day(5, 20)), today))
        assertFalse(salmon.expired(day(5, 20)))
        assertTrue(salmon.expired(day(5, 21)))
    }

    @Test
    fun `a trip with no length counts as a day`() {
        assertEquals(Trip(day(5, 1)), Schedule(departures = listOf(Departure(day(5, 1)))).trips(today).single())
    }
}
