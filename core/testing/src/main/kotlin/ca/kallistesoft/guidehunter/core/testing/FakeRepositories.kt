package ca.kallistesoft.guidehunter.core.testing

import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Species
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Guides a test sets ([guides]) and contacts it hands out; counts the contacts read. */
class FakeGuideRepository(
    guides: List<Guide> = TestGuides.all,
    var contacts: Map<String, GuideContact> = TestGuides.contacts,
) : GuideRepository {
    override val guides = MutableStateFlow(guides)
    val contactsRead = mutableListOf<String>()

    /** Set to make [contact] throw, like Firestore offline. */
    var failure: Exception? = null

    /** Set to hold a contact read until it's completed, like a slow network. */
    var onContact: CompletableDeferred<Unit>? = null

    override suspend fun contact(guideId: String): GuideContact? {
        contactsRead += guideId
        onContact?.await()
        failure?.let { throw it }
        return contacts[guideId]
    }
}

/** Signs in whoever's token it's given ([tokens] maps a token to its user); [failure] makes the next calls throw. */
class FakeAuthRepository(signedIn: GuideUser? = null) : AuthRepository {
    override val user = MutableStateFlow(signedIn)
    var tokens: Map<String, GuideUser> = mapOf(TOKEN to TestGuides.user)
    var failure: Exception? = null
    val reauthenticated = mutableListOf<String>()
    var deleted = false
        private set

    /** Set to hold a sign-in until it's completed, like a slow network. */
    var onSignIn: CompletableDeferred<Unit>? = null

    override suspend fun signIn(googleIdToken: String) {
        onSignIn?.await()
        failure?.let { throw it }
        user.value = tokens.getValue(googleIdToken)
    }

    override suspend fun reauthenticate(googleIdToken: String) {
        failure?.let { throw it }
        check(tokens[googleIdToken] == user.value) { "another account" }
        reauthenticated += googleIdToken
    }

    override suspend fun signOut() {
        user.value = null
    }

    override suspend fun deleteUser() {
        failure?.let { throw it }
        deleted = true
        user.value = null
    }

    companion object {
        /** The token that signs in [TestGuides.user]. */
        const val TOKEN = "google-token"
    }
}

/**
 * Guides' profiles and offers in memory, by user id. [failure] makes the next writes throw, [onWrite] holds them
 * until completed; [mails] counts the offers emailed for review.
 */
class FakeGuideAccountRepository : GuideAccountRepository {
    val profiles = MutableStateFlow<Map<String, GuideProfile>>(emptyMap())
    val offersByUser = MutableStateFlow<Map<String, List<GuideOffer>>>(emptyMap())
    val mails = mutableListOf<Pair<String, Boolean>>()
    var failure: Exception? = null
    var onWrite: CompletableDeferred<Unit>? = null
    private var nextId = 1

    override fun profile(uid: String) = profiles.map { it[uid] }

    override suspend fun saveProfile(uid: String, profile: GuideProfile) {
        write()
        profiles.value += uid to profile.trimmed()
    }

    override fun offers(uid: String) = offersByUser.map { it[uid].orEmpty() }

    /** Set to make [offer] throw, like Firestore offline with nothing cached. */
    var readFailure: Exception? = null

    override suspend fun offer(uid: String, id: String): GuideOffer? {
        readFailure?.let { throw it }
        return offersByUser.value[uid]?.find { it.id == id }
    }

    override suspend fun saveOffer(uid: String, id: String?, draft: OfferDraft, profile: GuideProfile): String {
        write()
        val existing = id?.let { offer(uid, it) }
        val saved = GuideOffer(id ?: "offer-${nextId++}", draft, OfferStatus.PENDING, live = existing?.live == true)
        val others = offersByUser.value[uid].orEmpty().filter { it.id != saved.id }
        offersByUser.value += uid to listOf(saved) + others
        mails += saved.id to (existing != null)
        return saved.id
    }

    override suspend fun deleteOffer(uid: String, offer: GuideOffer) {
        write()
        offersByUser.value += uid to offersByUser.value[uid].orEmpty().filter { it.id != offer.id }
    }

    override suspend fun deleteAll(uid: String) {
        write()
        offersByUser.value -= uid
        profiles.value -= uid
    }

    /** Puts [offers] in [uid]'s account, as if sent (and reviewed) before. */
    fun give(uid: String, vararg offers: GuideOffer) {
        offersByUser.value += uid to offers.toList()
    }

    private suspend fun write() {
        onWrite?.await()
        failure?.let { throw it }
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

/** Settings in memory; [settings] is what a test starts from (a first launch by default). */
class FakeSettingsRepository(settings: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(settings)

    override suspend fun finishOnboarding(mode: AppMode) {
        settings.value = settings.value.copy(onboarded = true, mode = mode)
    }

    override suspend fun setMode(mode: AppMode) {
        settings.value = settings.value.copy(mode = mode)
    }

    override suspend fun setSpeciesFilter(species: Set<Species>) {
        settings.value = settings.value.copy(speciesFilter = species)
    }

    override suspend fun reset() {
        settings.value = AppSettings()
    }
}
