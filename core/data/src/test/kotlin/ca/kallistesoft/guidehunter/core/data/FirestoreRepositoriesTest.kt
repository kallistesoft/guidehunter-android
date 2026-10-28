package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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

    // A guide's account: offers in `guideOffers` (one live, one waiting), the profile in `guideProfiles`.
    private val offers = mockk<CollectionReference>()
    private val profiles = mockk<CollectionReference>()
    private val guides = mockk<CollectionReference>()
    private val contacts = mockk<CollectionReference>()
    private val mails = mockk<CollectionReference>()
    private val mine = mockk<Query>()
    private val published = mockk<Query>()
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val batch = mockk<WriteBatch>(relaxed = true)
    private val refs = mutableMapOf<String, DocumentReference>()

    /** Made up front: MockK can't create a mock inside `every { }` or `verify { }`. */
    private fun ref(path: String) = refs.getValue(path)

    private val liveDoc = TestGuides.offer.toDocument("u1", live = true) + ("status" to "PUBLISHED")
    private val pendingDoc = TestGuides.offer.copy(title = "Walleye").toDocument("u1", live = false)

    private fun account(): FirestoreGuideAccountRepository {
        val paths = listOf("guideProfiles/u1", "mail/m", "guideOffers/new", "guideOffers/gone") +
            listOf("live", "pending").flatMap { id -> listOf("guideOffers/$id", "guides/$id", "guideContacts/$id") }
        for (path in paths) refs[path] = mockk(relaxed = true) { every { id } returns path.substringAfter('/') }
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(document("live", liveDoc), document("pending", pendingDoc)) }
        every { firestore.collection("guideOffers") } returns offers
        every { firestore.collection("guideProfiles") } returns profiles
        every { firestore.collection("guides") } returns guides
        every { firestore.collection("guideContacts") } returns contacts
        every { firestore.collection("mail") } returns mails
        every { offers.whereEqualTo("ownerUid", "u1") } returns mine
        every { mine.get() } returns Tasks.forResult(snapshot)
        for ((collection, name) in listOf(offers to "guideOffers", profiles to "guideProfiles", guides to "guides", contacts to "guideContacts")) {
            every { collection.document(any()) } answers { ref("$name/${firstArg<String>()}") }
        }
        every { offers.document() } returns ref("guideOffers/new")
        every { mails.document() } returns ref("mail/m")
        every { firestore.batch() } returns batch
        every { batch.commit() } returns Tasks.forResult(null)
        every { guides.whereEqualTo("ownerUid", "u1") } returns published
        return FirestoreGuideAccountRepository(firestore, "owner@example.com") { now }
    }

    @Test
    fun `the profile is followed live`() = runTest {
        val repository = account()
        val listener = slot<EventListener<DocumentSnapshot>>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        val profile = ref("guideProfiles/u1")
        val empty = document("u1", null)
        val saved = document("u1", TestGuides.profile.toDocument())
        every { profile.addSnapshotListener(capture(listener)) } returns registration
        val received = mutableListOf<GuideProfile?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.profile("u1").toList(received) }
        listener.captured.onEvent(empty, null)
        listener.captured.onEvent(saved, null)
        listener.captured.onEvent(null, mockk())
        job.cancel()
        assertEquals(listOf(null, TestGuides.profile), received)
        verify { registration.remove() }
    }

    @Test
    fun `the offers are the guide's own, followed live`() = runTest {
        val repository = account()
        val listener = slot<EventListener<QuerySnapshot>>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        every { mine.addSnapshotListener(capture(listener)) } returns registration
        val boosts = slot<EventListener<QuerySnapshot>>()
        val boostsRegistration = mockk<ListenerRegistration>(relaxed = true)
        every { published.addSnapshotListener(capture(boosts)) } returns boostsRegistration
        val received = mutableListOf<List<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.offers("u1").collect { received += it.map(GuideOffer::id) } }
        val pending = document("pending", pendingDoc)
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(pending) }
        listener.captured.onEvent(snapshot, null)
        boosts.captured.onEvent(null, mockk()) // offline with nothing cached: the offers show without their boosts
        listener.captured.onEvent(null, mockk())
        boosts.captured.onEvent(null, mockk())
        job.cancel()
        assertEquals(listOf(listOf("pending")), received)
        verify { registration.remove() }
        verify { boostsRegistration.remove() }
    }

    @Test
    fun `the offers on the map carry their boost, read from their published copy`() = runTest {
        val repository = account()
        val listener = slot<EventListener<QuerySnapshot>>()
        every { mine.addSnapshotListener(capture(listener)) } returns mockk(relaxed = true)
        val boosts = slot<EventListener<QuerySnapshot>>()
        every { published.addSnapshotListener(capture(boosts)) } returns mockk(relaxed = true)
        val received = mutableListOf<Map<String, Instant?>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.offers("u1").collect { offers -> received += offers.associate { it.id to it.boostedUntil } }
        }
        listener.captured.onEvent(mockk { every { documents } returns listOf(document("live", liveDoc), document("pending", pendingDoc)) }, null)
        val until = now + 3.days
        val boosted = document("live", mapOf("title" to "T") + boostUpdate(until))
        // A published copy left behind by a deleted offer is no offer's boost.
        val stale = document("pending", boostUpdate(until))
        boosts.captured.onEvent(mockk { every { documents } returns listOf(boosted, stale) }, null)
        boosts.captured.onEvent(null, mockk()) // a later error keeps the boosts
        job.cancel()
        assertEquals(listOf(mapOf("live" to until, "pending" to null)), received)
    }

    @Test
    fun `a boost sets only the published offer's end, 3 days from now`() = runTest {
        val repository = account()
        every { ref("guides/live").update(any<Map<String, Any>>()) } returns Tasks.forResult(null)
        repository.boost("u1", "live")
        verify { ref("guides/live").update(boostUpdate(now + 3.days)) }
    }

    @Test
    fun `one offer is read by its id`() = runTest {
        val repository = account()
        val live = document("live", liveDoc)
        val gone = document("gone", null)
        every { ref("guideOffers/live").get() } returns Tasks.forResult(live)
        every { ref("guideOffers/gone").get() } returns Tasks.forResult(gone)
        assertEquals(GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true), repository.offer("u1", "live"))
        assertNull(repository.offer("u1", "gone"))
    }

    @Test
    fun `saving the profile updates the contact of the offers on the map`() = runTest {
        account().saveProfile("u1", TestGuides.profile)
        verify { batch.set(ref("guideProfiles/u1"), TestGuides.profile.toDocument()) }
        verify { batch.update(ref("guideContacts/live"), TestGuides.profile.toContact()) }
        verify(exactly = 0) { batch.update(ref("guideContacts/pending"), any<Map<String, Any>>()) }
        verify { batch.commit() }
    }

    @Test
    fun `a new offer and its email are written together`() = runTest {
        val id = account().saveOffer("u1", null, TestGuides.offer, TestGuides.profile)
        assertEquals("new", id)
        verify { batch.set(ref("guideOffers/new"), TestGuides.offer.toDocument("u1", live = false)) }
        verify { batch.set(ref("mail/m"), offerMail("owner@example.com", "new", "u1", TestGuides.offer, TestGuides.profile, edited = false)) }
        verify { batch.commit() }
    }

    @Test
    fun `an edit keeps the offer on the map while it's reviewed again`() = runTest {
        val repository = account()
        val live = document("live", liveDoc)
        every { ref("guideOffers/live").get() } returns Tasks.forResult(live)
        val edit = TestGuides.offer.copy(priceFrom = "2600")
        assertEquals("live", repository.saveOffer("u1", "live", edit, TestGuides.profile))
        verify { batch.set(ref("guideOffers/live"), edit.toDocument("u1", live = true)) }
        verify { batch.set(ref("mail/m"), offerMail("owner@example.com", "live", "u1", edit, TestGuides.profile, edited = true)) }
    }

    @Test
    fun `deleting an offer on the map takes it off`() = runTest {
        val repository = account()
        repository.deleteOffer("u1", GuideOffer("pending", TestGuides.offer))
        verify { batch.delete(ref("guideOffers/pending")) }
        verify(exactly = 0) { batch.delete(ref("guides/pending")) }
        repository.deleteOffer("u1", GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true))
        verify { batch.delete(ref("guideOffers/live")) }
        verify { batch.delete(ref("guides/live")) }
        verify { batch.delete(ref("guideContacts/live")) }
    }

    @Test
    fun `deleting everything removes every offer and the profile`() = runTest {
        account().deleteAll("u1")
        listOf("guideOffers/live", "guides/live", "guideContacts/live", "guideOffers/pending", "guideProfiles/u1").forEach {
            verify { batch.delete(ref(it)) }
        }
        verify(exactly = 0) { batch.delete(ref("guides/pending")) }
        verify(exactly = 1) { batch.commit() }
    }
}
