package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.GuideContact
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Published guides from Firestore, live: an offer added in the console shows on the map at once. */
internal class FirestoreGuideRepository(private val firestore: FirebaseFirestore) : GuideRepository {
    override val guides: Flow<List<Guide>> = callbackFlow {
        val registration = firestore.collection(Collections.GUIDES).addSnapshotListener { snapshot, _ ->
            // An error (offline with an empty cache, rules) keeps the last list: the map never empties on a hiccup.
            if (snapshot != null) trySend(snapshot.documents.mapNotNull { guideFrom(it.id, it.data.orEmpty()) })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun contact(guideId: String): GuideContact? {
        val document = firestore.collection(Collections.CONTACTS).document(guideId).get().await()
        return contactFrom(document.data.orEmpty())
    }
}

/**
 * Saves the form in `guideApplications` and, in the same batch, a `mail` document that the "Trigger Email from
 * Firestore" extension sends to [notifyEmail]. Both are create-only for the app (firestore.rules).
 */
internal class FirestoreGuideApplicationRepository(
    private val firestore: FirebaseFirestore,
    private val notifyEmail: String,
) : GuideApplicationRepository {
    override suspend fun submit(application: GuideApplication) {
        val batch = firestore.batch()
        batch.set(firestore.collection(Collections.APPLICATIONS).document(), application.toDocument())
        batch.set(firestore.collection(Collections.MAIL).document(), application.toMail(notifyEmail))
        batch.commit().await()
    }
}
