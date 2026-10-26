package ca.kallistesoft.guidehunter.core.testing

import ca.kallistesoft.guidehunter.core.data.GuideApplicationRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.GuideContact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Guides a test sets ([guides]) and contacts it hands out; counts the contacts read. */
class FakeGuideRepository(
    guides: List<Guide> = TestGuides.all,
    var contacts: Map<String, GuideContact> = TestGuides.contacts,
) : GuideRepository {
    override val guides = MutableStateFlow(guides)
    val contactsRead = mutableListOf<String>()

    /** Set to make [contact] throw, like Firestore offline. */
    var failure: Exception? = null

    override suspend fun contact(guideId: String): GuideContact? {
        contactsRead += guideId
        failure?.let { throw it }
        return contacts[guideId]
    }
}

/** Keeps the applications sent; [failure] makes the next ones throw, [onSubmit] holds them until completed. */
class FakeGuideApplicationRepository : GuideApplicationRepository {
    val submitted = mutableListOf<GuideApplication>()
    var failure: Exception? = null
    var onSubmit: CompletableDeferred<Unit>? = null

    override suspend fun submit(application: GuideApplication) {
        onSubmit?.await()
        failure?.let { throw it }
        submitted += application
    }
}

/** A location a test sets; null as if the permission were denied. Counts the reads. */
class FakeLocationRepository(var location: GeoPoint? = null) : LocationRepository {
    var reads = 0
        private set

    override suspend fun current(): GeoPoint? {
        reads++
        return location
    }
}
