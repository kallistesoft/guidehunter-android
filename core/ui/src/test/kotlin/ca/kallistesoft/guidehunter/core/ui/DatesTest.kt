package ca.kallistesoft.guidehunter.core.ui

import android.app.Application
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.TripLength
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatesTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val may20 = LocalDate(2027, 5, 20)

    // Android puts thin spaces around the dash.
    @Test
    fun `dates in English, the shared month and year written once`() {
        assertEquals("May 20\u2009–\u200926, 2027", app.tripDates(Trip(may20, LocalDate(2027, 5, 26))))
        assertEquals("May 29\u2009–\u2009Jun 4, 2027", app.tripDates(Trip(LocalDate(2027, 5, 29), LocalDate(2027, 6, 4))))
        assertEquals("May 20, 2027", app.date(may20))
        assertEquals("May 20, 2027, morning", app.tripDates(Trip(may20, slot = Slot.MORNING)))
        assertEquals("May 20, 2027, afternoon", app.departureDate(Departure(may20, Slot.AFTERNOON)))
        assertEquals("May 20, 2027", app.departureDate(Departure(may20)))
    }

    @Test
    fun `trip lengths`() {
        assertEquals("Half day", app.tripLength(TripLength.HalfDay))
        assertEquals("1 day", app.tripLength(TripLength.Days(1)))
        assertEquals("3 days", app.tripLength(TripLength.Days(3)))
        assertEquals("1 week", app.tripLength(TripLength.Days(7)))
        assertEquals("2 weeks", app.tripLength(TripLength.Days(14)))
        assertEquals("10 days", app.tripLength(TripLength.Days(10)))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `dates and lengths in French`() {
        assertEquals("Demi-journée", app.tripLength(TripLength.HalfDay))
        assertEquals("1 semaine", app.tripLength(TripLength.Days(7)))
        assertEquals("3 jours", app.tripLength(TripLength.Days(3)))
        assertEquals(true, app.tripDates(Trip(may20, slot = Slot.MORNING)).endsWith(", matin"))
        assertEquals(true, "mai" in app.date(may20))
    }
}
