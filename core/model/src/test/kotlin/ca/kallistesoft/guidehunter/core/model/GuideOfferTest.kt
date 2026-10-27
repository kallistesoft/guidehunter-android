package ca.kallistesoft.guidehunter.core.model

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
    )

    @Test
    fun `a complete offer can be sent, the price being optional`() {
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
    }

    @Test
    fun `a species outside the activity is an error`() {
        assertEquals(setOf(OfferField.SPECIES), valid.copy(species = setOf(Species.WALLEYE)).errors())
        assertTrue(valid.copy(activity = GuideActivity.BOTH, species = setOf(Species.WALLEYE)).errors().isEmpty())
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
