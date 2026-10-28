package ca.kallistesoft.guidehunter.core.testing

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.TripLength
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Guides for tests: one per activity, at known places. Their departures repeat every year, so they never expire
 * whatever the clock; tests about dates use [today].
 */
object TestGuides {
    /** The day date tests run on. */
    val today = LocalDate(2027, 4, 15)

    /** The time tests run at: noon (UTC) on [today]. */
    val now: Instant = Instant.parse("2027-04-15T12:00:00Z")

    val bear = Guide(
        id = "bear",
        title = "Bear hunt",
        description = "Spring bear over bait.",
        activity = GuideActivity.HUNT,
        species = listOf(Species.BLACK_BEAR),
        region = "Abitibi",
        location = GeoPoint(48.0, -79.0),
        priceFrom = 2_400,
        schedule = Schedule(
            TripLength.Days(7),
            listOf(LocalDate(2027, 5, 1), LocalDate(2027, 5, 15), LocalDate(2027, 6, 5)).map { Departure(it) },
            everyYear = true,
        ),
    )
    val salmon = Guide(
        id = "salmon",
        title = "Salmon fishing",
        description = "Fly fishing on the Gaspé rivers.",
        activity = GuideActivity.FISH,
        species = listOf(Species.ATLANTIC_SALMON),
        region = "Gaspé",
        location = GeoPoint(48.8, -64.5),
        schedule = Schedule(
            TripLength.HalfDay,
            listOf(Departure(LocalDate(2027, 6, 10), Slot.MORNING), Departure(LocalDate(2027, 6, 10), Slot.AFTERNOON)),
            everyYear = true,
        ),
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
        // Dates on request.
        schedule = Schedule(TripLength.Days(5)),
    )
    val all = listOf(bear, salmon, moose)

    val bearContact = GuideContact("Bear Outfitter", phone = "+1 819 555 0100", email = "bear@example.com", website = "example.com")
    val contacts = mapOf("bear" to bearContact, "moose" to GuideContact("Moose Camp"))

    /** Quebec City: about 470 km from [bear]. */
    val quebecCity = GeoPoint(46.81, -71.21)

    /** A guide signed in with Google. */
    val user = GuideUser(uid = "guide-1", email = "guide@example.com", name = "Jo Guide")

    /** A complete profile. */
    val profile = GuideProfile(businessName = "Pourvoirie Test", email = "guide@example.com", phone = "819 555 0100")

    /** A complete offer. */
    val offer = OfferDraft(
        title = "Black bear hunt in Abitibi",
        description = "Guided bear hunts in the boreal forest, lodging included.",
        activity = GuideActivity.HUNT,
        species = setOf(Species.BLACK_BEAR),
        region = "Abitibi",
        location = GeoPoint(48.0, -79.0),
        priceFrom = "2400",
        schedule = Schedule(TripLength.Days(7), listOf(Departure(LocalDate(2027, 5, 1)))),
    )
}
