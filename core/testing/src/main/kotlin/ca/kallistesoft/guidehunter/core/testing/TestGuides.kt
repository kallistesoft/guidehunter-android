package ca.kallistesoft.guidehunter.core.testing

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.Species

/** Guides for tests: one per activity, at known places. */
object TestGuides {
    val bear = Guide(
        id = "bear",
        title = "Bear hunt",
        description = "Spring bear over bait.",
        activity = GuideActivity.HUNT,
        species = listOf(Species.BLACK_BEAR),
        region = "Abitibi",
        location = GeoPoint(48.0, -79.0),
        priceFrom = 2_400,
    )
    val salmon = Guide(
        id = "salmon",
        title = "Salmon fishing",
        description = "Fly fishing on the Gaspé rivers.",
        activity = GuideActivity.FISH,
        species = listOf(Species.ATLANTIC_SALMON),
        region = "Gaspé",
        location = GeoPoint(48.8, -64.5),
    )
    val moose = Guide(
        id = "moose",
        title = "Moose and walleye",
        description = "Moose in the fall, walleye in summer.",
        activity = GuideActivity.BOTH,
        species = listOf(Species.MOOSE, Species.WALLEYE),
        region = "La Tuque",
        location = GeoPoint(47.4, -72.8),
        priceFrom = 1_800,
    )
    val all = listOf(bear, salmon, moose)

    val bearContact = GuideContact("Bear Outfitter", phone = "+1 819 555 0100", email = "bear@example.com", website = "example.com")
    val contacts = mapOf("bear" to bearContact, "moose" to GuideContact("Moose Camp"))

    /** Quebec City: about 470 km from [bear]. */
    val quebecCity = GeoPoint(46.81, -71.21)

    /** A form that can be sent. */
    val application = GuideApplication(
        businessName = "Pourvoirie Test",
        description = "Guided bear hunts in the boreal forest, lodging included.",
        activity = GuideActivity.HUNT,
        species = setOf(Species.BLACK_BEAR),
        region = "Abitibi",
        location = GeoPoint(48.0, -79.0),
        priceFrom = "2400",
        email = "guide@example.com",
        phone = "819 555 0100",
    )
}
