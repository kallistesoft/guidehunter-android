package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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

    override suspend fun contact(guideId: String): GuideContact? {
        val document = firestore.collection(Collections.CONTACTS).document(guideId).get().await()
        return contactFrom(document.data.orEmpty())
    }
}

/**
 * A guide's profile and offers in Firestore (firestore.rules lets each guide reach only their own). Saving an offer
 * writes, in the same batch, a `mail` document that the "Trigger Email from Firestore" extension sends to
 * [notifyEmail] for review.
 */
internal class FirestoreGuideAccountRepository(
    private val firestore: FirebaseFirestore,
    private val notifyEmail: String,
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

    override fun offers(uid: String): Flow<List<GuideOffer>> = callbackFlow {
        val registration = offers.whereEqualTo(OWNER, uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(offersFrom(snapshot.documents.map { it.id to it.data.orEmpty() }))
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
        val batch = firestore.batch()
        delete(batch, offer)
        batch.commit().await()
    }

    override suspend fun deleteAll(uid: String) {
        val batch = firestore.batch()
        mine(uid).forEach { delete(batch, it) }
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
