package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Firestore documents as the console writes them and as the app sends them. */
class FirestoreDocumentsTest {
    private val document = mapOf(
        "title" to "Bear hunt",
        "description" to "Over bait.",
        "activity" to "HUNT",
        "species" to listOf("BLACK_BEAR", "DODO", 3),
        "region" to "Abitibi",
        "lat" to 48L,
        "lng" to -79.5,
        "priceFrom" to 2400L,
        "currency" to "USD",
    )

    @Test
    fun `a guide document becomes a guide, unknown species dropped`() {
        assertEquals(
            Guide("g1", "Bear hunt", "Over bait.", GuideActivity.HUNT, listOf(Species.BLACK_BEAR), "Abitibi", GeoPoint(48.0, -79.5), 2400, "USD"),
            guideFrom("g1", document),
        )
    }

    @Test
    fun `optional fields have defaults`() {
        val guide = guideFrom("g1", mapOf("title" to "T", "activity" to "FISH", "lat" to 1.0, "lng" to 2.0))
        assertEquals(Guide("g1", "T", "", GuideActivity.FISH, emptyList(), "", GeoPoint(1.0, 2.0)), guide)
    }

    @Test
    fun `a guide missing a required field isn't shown`() {
        listOf("title", "activity", "lat", "lng").forEach { field ->
            assertNull("without $field", guideFrom("g1", document - field))
        }
        assertNull(guideFrom("g1", document + ("activity" to "SKIING")))
        assertNull(guideFrom("g1", document + ("title" to " ")))
    }

    @Test
    fun `a contact needs a name`() {
        assertEquals(
            GuideContact("Outfitter", "819", "a@b.c", "x.com"),
            contactFrom(mapOf("name" to "Outfitter", "phone" to "819", "email" to "a@b.c", "website" to "x.com")),
        )
        assertEquals(GuideContact("Outfitter"), contactFrom(mapOf("name" to "Outfitter")))
        assertNull(contactFrom(emptyMap()))
        assertNull(contactFrom(mapOf("name" to "")))
    }

    @Test
    fun `a profile is saved trimmed, and read back`() {
        val doc = TestGuides.profile.copy(businessName = " Pourvoirie Test ").toDocument()
        assertEquals("Pourvoirie Test", doc["businessName"])
        assertEquals(TestGuides.profile, profileFrom(doc))
        assertNull(profileFrom(null))
        assertEquals(GuideProfile(), profileFrom(emptyMap()))
        assertEquals(
            mapOf("name" to "Pourvoirie Test", "email" to "guide@example.com", "phone" to "819 555 0100", "website" to ""),
            TestGuides.profile.toContact(),
        )
    }

    @Test
    fun `an offer is saved trimmed and waiting for review, and read back`() {
        val doc = TestGuides.offer.copy(title = " Bear hunt ", priceFrom = "").toDocument("u1", live = true)
        assertEquals("u1", doc["ownerUid"])
        assertEquals("Bear hunt", doc["title"])
        assertEquals("HUNT", doc["activity"])
        assertEquals(listOf("BLACK_BEAR"), doc["species"])
        assertEquals(48.0 to -79.0, doc["lat"] to doc["lng"])
        assertNull(doc["priceFrom"])
        assertEquals("PENDING", doc["status"])
        assertEquals(true, doc["live"])
        assertEquals(FieldValue.serverTimestamp(), doc["updatedAt"])
        assertEquals(2400, TestGuides.offer.toDocument("u1", live = false)["priceFrom"])

        val published = TestGuides.offer.toDocument("u1", live = true) + ("status" to "PUBLISHED") + ("lat" to 48L)
        assertEquals(GuideOffer("o1", TestGuides.offer, OfferStatus.PUBLISHED, live = true), offerFrom("o1", published))
    }

    @Test
    fun `a half-written offer reads with blanks, and an unknown status as waiting`() {
        val offer = offerFrom("o1", mapOf("status" to "ARCHIVED", "lat" to 1.0, "activity" to "SKI"))
        assertEquals(GuideOffer("o1", OfferDraft()), offer)
    }

    @Test
    fun `offers come latest first, one just saved at the top`() {
        val offers = offersFrom(
            listOf(
                "old" to mapOf("updatedAt" to Timestamp(100, 0)),
                "saving" to mapOf<String, Any?>("updatedAt" to null),
                "new" to mapOf("updatedAt" to Timestamp(200, 0)),
            ),
        )
        assertEquals(listOf("saving", "new", "old"), offers.map { it.id })
    }

    @Test
    fun `the email goes to us, with the guide to reply to, every field and how to approve`() {
        val mail = offerMail("owner@example.com", "o1", "u1", TestGuides.offer, TestGuides.profile, edited = false)
        assertEquals("owner@example.com", mail["to"])
        assertEquals("guide@example.com", mail["replyTo"])
        val message = mail["message"] as Map<*, *>
        assertEquals("New offer: Black bear hunt in Abitibi (Pourvoirie Test)", message["subject"])
        val text = message["text"] as String
        listOf(
            "Offers: HUNT", "Species: BLACK_BEAR", "Location: 48.0, -79.0", "Price from: 2400", "boreal forest",
            "Phone: 819 555 0100", "approve-offer.py <project> o1 (guide u1)",
        ).forEach { assertTrue("has $it", it in text) }

        val edited = offerMail("o@e.com", "o1", "u1", TestGuides.offer.copy(priceFrom = ""), TestGuides.profile, edited = true)
        val editedMessage = edited["message"] as Map<*, *>
        assertTrue((editedMessage["subject"] as String).startsWith("Offer edited: "))
        assertTrue("on request" in editedMessage["text"] as String)

        val long = offerMail("o@e.com", "o1", "u1", TestGuides.offer.copy(title = "x".repeat(400)), TestGuides.profile, false)
        assertEquals(300, ((long["message"] as Map<*, *>)["subject"] as String).length)
    }
}
