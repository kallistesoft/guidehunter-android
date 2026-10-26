package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Firestore adapters, on a mocked Firestore: what is read and written where. */
class FirestoreRepositoriesTest {
    private val firestore = mockk<FirebaseFirestore>()

    private fun document(id: String, data: Map<String, Any?>?) = mockk<DocumentSnapshot> {
        every { this@mockk.id } returns id
        every { this@mockk.data } returns data
    }

    @Test
    fun `guides follow the collection live, keeping the last list on an error`() = runTest {
        val listener = slot<EventListener<QuerySnapshot>>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        every { firestore.collection("guides") } returns mockk<CollectionReference> {
            every { addSnapshotListener(capture(listener)) } returns registration
        }
        val received = mutableListOf<List<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            FirestoreGuideRepository(firestore, UnconfinedTestDispatcher(testScheduler)).guides
                .collect { guides -> received += guides.map { it.id } }
        }
        val good = mapOf("title" to "T", "activity" to "HUNT", "lat" to 1.0, "lng" to 2.0)
        listener.captured.onEvent(mockk { every { documents } returns listOf(document("a", good), document("b", null)) }, null)
        listener.captured.onEvent(null, mockk())
        job.cancel()

        assertEquals(listOf(listOf("a")), received)
        verify { registration.remove() }
    }

    @Test
    fun `a contact is read from its own collection`() = runTest {
        val contacts = mockk<CollectionReference>()
        every { firestore.collection("guideContacts") } returns contacts
        every { contacts.document("bear") } returns mockk<DocumentReference> {
            every { get() } returns Tasks.forResult(document("bear", mapOf("name" to "Outfitter")))
        }
        every { contacts.document("none") } returns mockk<DocumentReference> {
            every { get() } returns Tasks.forResult(document("none", null))
        }
        val repository = FirestoreGuideRepository(firestore)
        assertEquals(GuideContact("Outfitter"), repository.contact("bear"))
        assertNull(repository.contact("none"))
    }

    @Test
    fun `an application and its email are written together`() = runTest {
        val application = mockk<DocumentReference>()
        val mail = mockk<DocumentReference>()
        every { firestore.collection("guideApplications") } returns mockk { every { document() } returns application }
        every { firestore.collection("mail") } returns mockk { every { document() } returns mail }
        val batch = mockk<WriteBatch>(relaxed = true)
        every { firestore.batch() } returns batch
        every { batch.commit() } returns Tasks.forResult(null)

        FirestoreGuideApplicationRepository(firestore, "owner@example.com").submit(TestGuides.application)

        verify { batch.set(application, TestGuides.application.toDocument()) }
        verify { batch.set(mail, TestGuides.application.toMail("owner@example.com")) }
        verify { batch.commit() }
    }
}
