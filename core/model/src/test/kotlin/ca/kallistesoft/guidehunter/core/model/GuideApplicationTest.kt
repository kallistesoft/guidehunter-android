package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideApplicationTest {
    private val valid = GuideApplication(
        businessName = "Pourvoirie Test",
        description = "Guided bear hunts in the boreal forest, lodging included.",
        activity = GuideActivity.HUNT,
        species = setOf(Species.BLACK_BEAR),
        region = "Abitibi",
        location = GeoPoint(48.0, -79.0),
        email = "guide@example.com",
    )

    @Test
    fun `a complete form can be sent, price, phone and website being optional`() {
        assertTrue(valid.errors().isEmpty())
        assertTrue(valid.copy(priceFrom = "2400", phone = "+1 (819) 555-0100", website = "example.com").errors().isEmpty())
    }

    @Test
    fun `an empty form lists every required field`() {
        assertEquals(
            setOf(
                ApplicationField.BUSINESS_NAME,
                ApplicationField.DESCRIPTION,
                ApplicationField.ACTIVITY,
                ApplicationField.SPECIES,
                ApplicationField.REGION,
                ApplicationField.LOCATION,
                ApplicationField.EMAIL,
            ),
            GuideApplication().errors(),
        )
    }

    @Test
    fun `each field is checked`() {
        assertEquals(setOf(ApplicationField.BUSINESS_NAME), valid.copy(businessName = "  ").errors())
        assertEquals(setOf(ApplicationField.DESCRIPTION), valid.copy(description = "Too short.").errors())
        assertEquals(setOf(ApplicationField.SPECIES), valid.copy(species = emptySet()).errors())
        assertEquals(setOf(ApplicationField.REGION), valid.copy(region = "").errors())
        assertEquals(setOf(ApplicationField.LOCATION), valid.copy(location = null).errors())
        assertEquals(setOf(ApplicationField.PRICE), valid.copy(priceFrom = "0").errors())
        assertEquals(setOf(ApplicationField.PRICE), valid.copy(priceFrom = "lots").errors())
        assertEquals(setOf(ApplicationField.EMAIL), valid.copy(email = "guide@example").errors())
        assertEquals(setOf(ApplicationField.PHONE), valid.copy(phone = "555").errors())
    }

    @Test
    fun `a species outside the activity is an error`() {
        assertEquals(setOf(ApplicationField.SPECIES), valid.copy(species = setOf(Species.WALLEYE)).errors())
        assertTrue(valid.copy(activity = GuideActivity.BOTH, species = setOf(Species.WALLEYE)).errors().isEmpty())
    }
}
