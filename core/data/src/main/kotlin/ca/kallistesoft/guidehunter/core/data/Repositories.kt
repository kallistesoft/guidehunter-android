package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.GuideContact
import kotlinx.coroutines.flow.Flow

/** The guides on the map. */
interface GuideRepository {
    /** Every published guide's public offer, updated live. Empty while offline with nothing cached. */
    val guides: Flow<List<Guide>>

    /** The guide's name and contact details, read only once the user has earned them; null if it has none. */
    suspend fun contact(guideId: String): GuideContact?
}

/** Where "Become a guide" forms go: to us, by email, for review. */
fun interface GuideApplicationRepository {
    /** Sends [application]; throws when it couldn't be sent (offline, not configured). */
    suspend fun submit(application: GuideApplication)
}

/** The user's location, to show how far a guide is. */
fun interface LocationRepository {
    /** A recent, coarse location; null without the location permission or when Android has none. */
    suspend fun current(): GeoPoint?
}

/** The backend isn't set up in this build (no `google-services.json`): see README, "Firebase". */
class BackendNotConfiguredException : IllegalStateException("Firebase is not configured in this build")
