package ca.kallistesoft.guidehunter.core.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideOfferTest {
    private val valid = OfferDraft(
        title = "Black bear hunt in Abitibi",
        description = "Guided bear hunts in the boreal forest, lodging included.",
        activity = GuideActivity.HUNT,
        species = setOf(Species.BLACK_BEAR),
        region = "Abitibi",
        location = GeoPoint(48.0, -79.0),
        schedule = Schedule(TripLength.Days(7)),
    )
    private val may1 = Departure(LocalDate(2027, 5, 1))

    @Test
    fun `a complete offer can be sent, the price and the dates being optional`() {
        assertTrue(valid.errors().isEmpty())
        assertTrue(valid.copy(priceFrom = "2400").errors().isEmpty())
    }

    @Test
    fun `an empty offer lists every required field`() {
        assertEquals(
            setOf(
                OfferField.TITLE,
                OfferField.DESCRIPTION,
                OfferField.ACTIVITY,
                OfferField.SPECIES,
                OfferField.REGION,
                OfferField.LOCATION,
                OfferField.DURATION,
            ),
            OfferDraft().errors(),
        )
    }

    @Test
    fun `each field is checked`() {
        assertEquals(setOf(OfferField.TITLE), valid.copy(title = "  ").errors())
        assertEquals(setOf(OfferField.DESCRIPTION), valid.copy(description = "Too short.").errors())
        assertEquals(setOf(OfferField.SPECIES), valid.copy(species = emptySet()).errors())
        assertEquals(setOf(OfferField.REGION), valid.copy(region = "").errors())
        assertEquals(setOf(OfferField.LOCATION), valid.copy(location = null).errors())
        assertEquals(setOf(OfferField.PRICE), valid.copy(priceFrom = "0").errors())
        assertEquals(setOf(OfferField.PRICE), valid.copy(priceFrom = "lots").errors())
        assertEquals(setOf(OfferField.DURATION), valid.copy(schedule = Schedule(TripLength.Days(0))).errors())
        assertEquals(setOf(OfferField.DURATION), valid.copy(schedule = Schedule(TripLength.Days(61))).errors())
    }

    @Test
    fun `departures have no duplicates, not too many, and a slot exactly on half days`() {
        val week = Schedule(TripLength.Days(7), listOf(may1))
        assertTrue(valid.copy(schedule = week).errors().isEmpty())
        assertEquals(setOf(OfferField.DATES), valid.copy(schedule = week.copy(departures = listOf(may1, may1))).errors())
        val many = (0..OfferDraft.MAX_DEPARTURES).map { Departure(LocalDate.fromEpochDays(20_000 + it)) }
        assertEquals(setOf(OfferField.DATES), valid.copy(schedule = week.copy(departures = many)).errors())
        assertEquals(setOf(OfferField.DATES), valid.copy(schedule = week.copy(departures = listOf(may1.copy(slot = Slot.MORNING)))).errors())
        val half = Schedule(TripLength.HalfDay, listOf(may1.copy(slot = Slot.MORNING), may1.copy(slot = Slot.AFTERNOON)))
        assertTrue(valid.copy(schedule = half).errors().isEmpty())
        assertEquals(setOf(OfferField.DATES), valid.copy(schedule = half.copy(departures = listOf(may1))).errors())
    }

    @Test
    fun `each step checks only its own fields`() {
        val empty = OfferDraft()
        assertEquals(setOf(OfferField.TITLE, OfferField.DESCRIPTION, OfferField.ACTIVITY), empty.errors(OfferStep.WHAT))
        assertEquals(setOf(OfferField.SPECIES), empty.errors(OfferStep.SPECIES))
        assertEquals(setOf(OfferField.REGION, OfferField.LOCATION), empty.errors(OfferStep.WHERE))
        assertEquals(setOf(OfferField.DURATION), empty.errors(OfferStep.WHEN))
        assertEquals(emptySet<OfferField>(), empty.errors(OfferStep.REVIEW))
    }

    @Test
    fun `a species outside the activity is an error`() {
        assertEquals(setOf(OfferField.SPECIES), valid.copy(species = setOf(Species.WALLEYE)).errors())
        assertTrue(valid.copy(activity = GuideActivity.FISH, species = setOf(Species.WALLEYE)).errors().isEmpty())
    }

    @Test
    fun `one type per offer, an older both has to choose`() {
        assertEquals(setOf(OfferField.ACTIVITY), valid.copy(activity = GuideActivity.BOTH).errors())
        assertEquals(listOf(GuideActivity.HUNT, GuideActivity.FISH), GuideActivity.forOffers)
    }

    @Test
    fun `a new offer waits for review and isn't on the map`() {
        val offer = GuideOffer("o1", valid)
        assertEquals(OfferStatus.PENDING, offer.status)
        assertFalse(offer.live)
    }
}

class GuideProfileTest {
    private val valid = GuideProfile(businessName = "Pourvoirie Test", email = "guide@example.com")

    @Test
    fun `a name and an email are enough, phone and website being optional`() {
        assertTrue(valid.isComplete)
        assertTrue(valid.copy(phone = "+1 (819) 555-0100", website = "example.com").isComplete)
    }

    @Test
    fun `each field is checked`() {
        assertEquals(setOf(ProfileField.BUSINESS_NAME, ProfileField.EMAIL), GuideProfile().errors())
        assertEquals(setOf(ProfileField.EMAIL), valid.copy(email = "guide@example").errors())
        assertEquals(setOf(ProfileField.PHONE), valid.copy(phone = "555").errors())
        assertFalse(valid.copy(businessName = " ").isComplete)
    }

    @Test
    fun `saved trimmed`() {
        assertEquals(
            GuideProfile("Camp", "a@b.ca", "819 555 0100", "example.com"),
            GuideProfile(" Camp ", " a@b.ca", "819 555 0100 ", " example.com ").trimmed(),
        )
    }
}
