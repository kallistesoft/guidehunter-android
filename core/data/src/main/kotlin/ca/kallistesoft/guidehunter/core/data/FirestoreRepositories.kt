package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.Boost
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.Now
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.UserContact
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.time.Instant

/**
 * Published guides from Firestore, live: an offer added in the console shows on the map at once. The documents are
 * parsed on [background] (the tests pass a test dispatcher).
 */
internal class FirestoreGuideRepository(
    private val firestore: FirebaseFirestore,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : GuideRepository {
    override val guides: Flow<List<Guide>> = callbackFlow<List<DocumentSnapshot>> {
        val registration = firestore.collection(Collections.GUIDES).addSnapshotListener { snapshot, _ ->
            // An error (offline with an empty cache, rules) keeps the last list: the map never empties on a hiccup.
            if (snapshot != null) trySend(snapshot.documents)
        }
        awaitClose { registration.remove() }
    }
        // Firestore calls back on the main thread; the parsing of every guide happens off it.
        .map { documents -> documents.mapNotNull { guideFrom(it.id, it.data.orEmpty()) } }
        .flowOn(background)
}

/**
 * A guide's profile and offers in Firestore (firestore.rules lets each guide reach only their own). Saving an offer
 * writes, in the same batch, a `mail` document that the "Trigger Email from Firestore" extension sends to
 * [notifyEmail] for review. A boost ends [ca.kallistesoft.guidehunter.core.model.Boost.DURATION] after [now].
 */
internal class FirestoreGuideAccountRepository(
    private val firestore: FirebaseFirestore,
    private val notifyEmail: String,
    private val now: Now,
) : GuideAccountRepository {
    private val offers get() = firestore.collection(Collections.OFFERS)

    override fun profile(uid: String): Flow<GuideProfile?> = callbackFlow {
        val registration = firestore.collection(Collections.PROFILES).document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(profileFrom(snapshot.data))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveProfile(uid: String, profile: GuideProfile) {
        val live = mine(uid).filter { it.live }
        val batch = firestore.batch()
        batch.set(firestore.collection(Collections.PROFILES).document(uid), profile.toDocument())
        // The contact shown with each offer on the map follows the profile (the offers themselves wait for review).
        live.forEach { batch.update(firestore.collection(Collections.CONTACTS).document(it.id), profile.toContact()) }
        batch.commit().await()
    }

    override fun offers(uid: String): Flow<List<GuideOffer>> = combine(written(uid), boosts(uid)) { offers, boosts ->
        offers.map { offer -> offer.copy(boostedUntil = boosts[offer.id]?.takeIf { offer.live }) }
    }

    /** The guide's offers as written. */
    private fun written(uid: String): Flow<List<GuideOffer>> = callbackFlow {
        val registration = offers.whereEqualTo(OWNER, uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(offersFrom(snapshot.documents.map { it.id to it.data.orEmpty() }))
        }
        awaitClose { registration.remove() }
    }

    /**
     * The boosts of the guide's offers on the map, from their published copies (anyone reads `guides`). Failing
     * before any came (offline with nothing cached), the offers show without them rather than not at all.
     */
    private fun boosts(uid: String): Flow<Map<String, Instant>> = callbackFlow {
        var received = false
        val registration = firestore.collection(Collections.GUIDES).whereEqualTo(OWNER, uid).addSnapshotListener { snapshot, _ ->
            when {
                snapshot != null -> trySend(boostsFrom(snapshot.documents.map { it.id to it.data.orEmpty() }))
                !received -> trySend(emptyMap())
            }
            received = true
        }
        awaitClose { registration.remove() }
    }

    override suspend fun offer(uid: String, id: String): GuideOffer? {
        val document = offers.document(id).get().await()
        return document.data?.let { offerFrom(id, it) }
    }

    override suspend fun saveOffer(uid: String, id: String?, draft: OfferDraft, profile: GuideProfile): String {
        val existing = id?.let { offer(uid, it) }
        val reference = if (id == null) offers.document() else offers.document(id)
        val batch = firestore.batch()
        batch.set(reference, draft.toDocument(uid, live = existing?.live == true))
        batch.set(
            firestore.collection(Collections.MAIL).document(),
            offerMail(notifyEmail, reference.id, uid, draft, profile, edited = existing != null),
        )
        batch.commit().await()
        return reference.id
    }

    override suspend fun deleteOffer(uid: String, offer: GuideOffer) {
        val requests = openRequests(firestore, uid, offer.id)
        val batch = firestore.batch()
        delete(batch, offer)
        requests.forEach { batch.update(it, statusFields(RequestStatus.OFFER_REMOVED)) }
        batch.commit().await()
    }

    override suspend fun boost(uid: String, offerId: String) {
        firestore.collection(Collections.GUIDES).document(offerId).update(boostUpdate(Boost.until(now()))).await()
    }

    override suspend fun deleteAll(uid: String) {
        val requests = openRequests(firestore, uid, offerId = null)
        val batch = firestore.batch()
        mine(uid).forEach { delete(batch, it) }
        requests.forEach { batch.update(it, statusFields(RequestStatus.OFFER_REMOVED)) }
        batch.delete(firestore.collection(Collections.PROFILES).document(uid))
        batch.commit().await()
    }

    /** The offer, and its published copy if it's on the map. */
    private fun delete(batch: WriteBatch, offer: GuideOffer) {
        batch.delete(offers.document(offer.id))
        if (offer.live) {
            batch.delete(firestore.collection(Collections.GUIDES).document(offer.id))
            batch.delete(firestore.collection(Collections.CONTACTS).document(offer.id))
        }
    }

    private suspend fun mine(uid: String): List<GuideOffer> =
        offers.whereEqualTo(OWNER, uid).get().await().documents.map { offerFrom(it.id, it.data.orEmpty()) }

    private companion object {
        const val OWNER = "ownerUid"
    }
}

/** The user's contact in Firestore (`userProfiles/{uid}`: firestore.rules lets only them reach it). */
internal class FirestoreUserAccountRepository(private val firestore: FirebaseFirestore) : UserAccountRepository {
    private fun document(uid: String) = firestore.collection(Collections.USER_PROFILES).document(uid)

    override fun contact(uid: String): Flow<UserContact?> = callbackFlow {
        val registration = document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(userContactFrom(snapshot.data))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveContact(uid: String, contact: UserContact) {
        document(uid).set(contact.toDocument()).await()
    }

    override suspend fun deleteAll(uid: String) {
        document(uid).delete().await()
    }
}

/**
 * Requests in Firestore (`requests/{offerId_userUid}`). firestore.rules lets the user and the guide read them, and
 * each side make only its own status changes.
 */
internal class FirestoreRequestRepository(private val firestore: FirebaseFirestore) : RequestRepository {
    private val requests get() = firestore.collection(Collections.REQUESTS)

    override fun sent(uid: String): Flow<List<InterestRequest>> = list(requests.whereEqualTo(USER, uid))

    override fun received(uid: String): Flow<List<InterestRequest>> = list(requests.whereEqualTo(GUIDE, uid))

    override fun request(offerId: String, uid: String): Flow<InterestRequest?> = callbackFlow {
        val id = InterestRequest.requestId(offerId, uid)
        val registration = requests.document(id).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.data?.let { requestFrom(id, it) })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun send(uid: String, guide: Guide, trip: Trip?, contact: UserContact) {
        requests.document(InterestRequest.requestId(guide.id, uid)).set(requestDocument(uid, guide, trip, contact)).await()
    }

    override suspend fun cancel(id: String) = update(id, statusFields(RequestStatus.CANCELLED))

    override suspend fun resend(id: String, trip: Trip?, contact: UserContact) = update(id, resendFields(trip, contact))

    override suspend fun approve(id: String, profile: GuideProfile) = update(id, approveFields(profile))

    override suspend fun reject(id: String) = update(id, statusFields(RequestStatus.REJECTED))

    override suspend fun markOfferRemoved(guideUid: String, offerId: String?) {
        val open = openRequests(firestore, guideUid, offerId)
        if (open.isEmpty()) return
        val batch = firestore.batch()
        open.forEach { batch.update(it, statusFields(RequestStatus.OFFER_REMOVED)) }
        batch.commit().await()
    }

    override suspend fun deleteSent(uid: String) {
        val sent = requests.whereEqualTo(USER, uid).get().await().documents
        if (sent.isEmpty()) return
        val batch = firestore.batch()
        sent.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    private suspend fun update(id: String, fields: Map<String, Any?>) {
        requests.document(id).set(fields, SetOptions.merge()).await()
    }

    private fun list(query: Query): Flow<List<InterestRequest>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(requestsFrom(snapshot.documents.map { it.id to it.data.orEmpty() }))
        }
        awaitClose { registration.remove() }
    }
}

private const val USER = "userUid"
private const val GUIDE = "guideUid"

/**
 * [guideUid]'s pending and accepted requests, for one offer or all of them: what deleting it closes. One equality
 * filter (no composite index); the rest is filtered here.
 */
private suspend fun openRequests(firestore: FirebaseFirestore, guideUid: String, offerId: String?): List<DocumentReference> =
    firestore.collection(Collections.REQUESTS).whereEqualTo(GUIDE, guideUid).get().await().documents
        .filter { document ->
            val data = document.data.orEmpty()
            (offerId == null || data["offerId"] == offerId) &&
                RequestStatus.entries.find { it.name == data["status"] }?.open == true
        }
        .map { it.reference }
