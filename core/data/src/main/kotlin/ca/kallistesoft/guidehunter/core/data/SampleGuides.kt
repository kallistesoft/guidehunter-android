package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.Species
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Made-up guides for a build without Firebase (a fresh clone, pull requests), so the map, the rewarded ad and the
 * contact details can be tried. Never shown once Firebase is configured.
 */
internal class SampleGuideRepository : GuideRepository {
    override val guides: Flow<List<Guide>> = flowOf(SAMPLE_GUIDES)

    override suspend fun contact(guideId: String): GuideContact? = SAMPLE_CONTACTS[guideId]
}

/** Without Firebase no one can sign in: the guide's screen says so. */
internal class UnconfiguredAuthRepository : AuthRepository {
    override val user: Flow<GuideUser?> = flowOf(null)

    override suspend fun signIn(googleIdToken: String) = throw BackendNotConfiguredException()

    override suspend fun reauthenticate(googleIdToken: String) = throw BackendNotConfiguredException()

    override suspend fun signOut() = Unit

    override suspend fun deleteUser() = throw BackendNotConfiguredException()
}

/** Without Firebase there's no account to read or write (and no one signed in to ask). */
internal class UnconfiguredGuideAccountRepository : GuideAccountRepository {
    override fun profile(uid: String): Flow<GuideProfile?> = flowOf(null)

    override suspend fun saveProfile(uid: String, profile: GuideProfile) = throw BackendNotConfiguredException()

    override fun offers(uid: String): Flow<List<GuideOffer>> = flowOf(emptyList())

    override suspend fun offer(uid: String, id: String): GuideOffer? = null

    override suspend fun saveOffer(uid: String, id: String?, draft: OfferDraft, profile: GuideProfile): String =
        throw BackendNotConfiguredException()

    override suspend fun deleteOffer(uid: String, offer: GuideOffer) = throw BackendNotConfiguredException()

    override suspend fun deleteAll(uid: String) = throw BackendNotConfiguredException()
}

internal val SAMPLE_GUIDES = listOf(
    Guide(
        id = "sample-bear",
        title = "Black bear over bait, Abitibi",
        description = "Spring bear hunt from heated blinds over well-run bait sites. Lodging and meals included.",
        activity = GuideActivity.HUNT,
        species = listOf(Species.BLACK_BEAR),
        region = "Abitibi-Témiscamingue, QC",
        location = GeoPoint(48.24, -79.02),
        priceFrom = 2_400,
    ),
    Guide(
        id = "sample-moose",
        title = "Moose and walleye, Haute-Mauricie",
        description = "Fall moose hunt by canoe, and walleye fishing on the reservoir in summer. Experienced callers.",
        activity = GuideActivity.BOTH,
        species = listOf(Species.MOOSE, Species.WALLEYE, Species.NORTHERN_PIKE),
        region = "La Tuque, QC",
        location = GeoPoint(47.44, -72.78),
        priceFrom = 1_800,
    ),
    Guide(
        id = "sample-salmon",
        title = "Atlantic salmon on the Gaspé rivers",
        description = "Fly fishing for Atlantic salmon with a certified guide. Beginners welcome, gear provided.",
        activity = GuideActivity.FISH,
        species = listOf(Species.ATLANTIC_SALMON, Species.BROOK_TROUT),
        region = "Gaspé, QC",
        location = GeoPoint(48.83, -64.48),
    ),
)

private val SAMPLE_CONTACTS = mapOf(
    "sample-bear" to GuideContact("Pourvoirie Exemple", phone = "+1 819 555 0100", email = "bear@example.com"),
    "sample-moose" to GuideContact("Exemple Outfitters", phone = "+1 819 555 0101", website = "example.com"),
    "sample-salmon" to GuideContact("Gaspé Fly Example", email = "salmon@example.com", website = "example.com/salmon"),
)
