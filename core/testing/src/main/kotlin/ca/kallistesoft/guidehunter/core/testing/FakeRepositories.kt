package ca.kallistesoft.guidehunter.core.testing

import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.RequestRepository
import ca.kallistesoft.guidehunter.core.data.UserAccountRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Boost
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Species
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** Guides a test sets ([guides]). */
class FakeGuideRepository(guides: List<Guide> = TestGuides.all) : GuideRepository {
    override val guides = MutableStateFlow(guides)
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
 * until completed; [mails] counts the offers emailed for review. A boost ends [Boost.DURATION] after [now]. Deleting
 * offers closes their requests in [requests].
 */
class FakeGuideAccountRepository(private val requests: FakeRequestRepository? = null) : GuideAccountRepository {
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
        requests?.markOfferRemoved(uid, offer.id)
    }

    /** The clock boosts start from. */
    var now: Instant = TestGuides.now

    override suspend fun boost(uid: String, offerId: String) {
        write()
        val offers = offersByUser.value[uid].orEmpty()
        // firestore.rules: only the guide's own offer, and only one on the map (it has a published copy).
        check(offers.any { it.id == offerId && it.live }) { "no live offer $offerId" }
        offersByUser.value += uid to offers.map { if (it.id == offerId) it.copy(boostedUntil = Boost.until(now)) else it }
    }

    override suspend fun deleteAll(uid: String) {
        write()
        offersByUser.value -= uid
        profiles.value -= uid
        requests?.markOfferRemoved(uid, null)
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

/** Users' contacts in memory, by user id; [failure] makes the next writes throw, [onWrite] holds them. */
class FakeUserAccountRepository : UserAccountRepository {
    val contacts = MutableStateFlow<Map<String, UserContact>>(emptyMap())
    var failure: Exception? = null
    var onWrite: CompletableDeferred<Unit>? = null

    override fun contact(uid: String) = contacts.map { it[uid] }

    override suspend fun saveContact(uid: String, contact: UserContact) {
        onWrite?.await()
        failure?.let { throw it }
        contacts.value += uid to contact.trimmed()
    }

    override suspend fun deleteAll(uid: String) {
        failure?.let { throw it }
        contacts.value -= uid
    }
}

/**
 * Requests in memory, by id, with the transitions firestore.rules allows (anything else throws, like a refused
 * write). Each write moves a clock on, so the latest change comes first. [failure] makes the next writes throw,
 * [onWrite] holds them until completed.
 */
class FakeRequestRepository : RequestRepository {
    val requests = MutableStateFlow<Map<String, InterestRequest>>(emptyMap())
    var failure: Exception? = null
    var onWrite: CompletableDeferred<Unit>? = null
    private var clock = 1_000L

    override fun sent(uid: String) = requests.map { all -> all.values.filter { it.userUid == uid }.sortedByDescending { it.updatedAt } }

    override fun received(uid: String) = requests.map { all -> all.values.filter { it.guideUid == uid }.sortedByDescending { it.updatedAt } }

    override fun request(offerId: String, uid: String) = requests.map { it[InterestRequest.requestId(offerId, uid)] }

    override suspend fun send(uid: String, guide: Guide, trip: Trip?, contact: UserContact) {
        write()
        val id = InterestRequest.requestId(guide.id, uid)
        check(id !in requests.value) { "already sent" }
        check(guide.ownerUid != uid) { "own offer" }
        val now = clock++
        requests.value += id to InterestRequest(
            id, guide.id, guide.title, trip, guide.ownerUid, uid, contact.trimmed(), RequestStatus.PENDING,
            createdAt = now, updatedAt = now,
        )
    }

    override suspend fun cancel(id: String) = change(id, RequestStatus.PENDING, RequestStatus.APPROVED) {
        it.copy(status = RequestStatus.CANCELLED)
    }

    override suspend fun resend(id: String, trip: Trip?, contact: UserContact) = change(id, RequestStatus.CANCELLED) {
        it.copy(status = RequestStatus.PENDING, trip = trip, user = contact.trimmed())
    }

    override suspend fun approve(id: String, profile: GuideProfile) = change(id, RequestStatus.PENDING) {
        val guide = profile.trimmed()
        it.copy(status = RequestStatus.APPROVED, guideContact = GuideContact(guide.businessName, guide.phone, guide.email, guide.website))
    }

    override suspend fun reject(id: String) = change(id, RequestStatus.PENDING) { it.copy(status = RequestStatus.REJECTED) }

    override suspend fun markOfferRemoved(guideUid: String, offerId: String?) {
        write()
        requests.value.values
            .filter { it.guideUid == guideUid && (offerId == null || it.offerId == offerId) && it.status.open }
            .forEach { request -> change(request.id, RequestStatus.PENDING, RequestStatus.APPROVED) { it.copy(status = RequestStatus.OFFER_REMOVED) } }
    }

    override suspend fun deleteSent(uid: String) {
        write()
        requests.value = requests.value.filterValues { it.userUid != uid }
    }

    /** Puts [request] in, as if sent before. */
    fun give(vararg request: InterestRequest) {
        requests.value += request.associateBy { it.id }
    }

    private suspend fun change(id: String, vararg from: RequestStatus, update: (InterestRequest) -> InterestRequest) {
        write()
        val request = checkNotNull(requests.value[id]) { "no request $id" }
        check(request.status in from) { "${request.status} can't change that way" }
        requests.value += id to update(request).copy(updatedAt = clock++)
    }

    private suspend fun write() {
        onWrite?.await()
        failure?.let { throw it }
    }
}
