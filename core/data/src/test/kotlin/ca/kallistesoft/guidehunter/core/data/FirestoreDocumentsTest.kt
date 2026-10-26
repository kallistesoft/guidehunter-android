package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.TestGuides
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
    fun `an application is saved trimmed`() {
        val sent = TestGuides.application.copy(businessName = " Pourvoirie Test ", priceFrom = "")
        val doc = sent.toDocument()
        assertEquals("Pourvoirie Test", doc["businessName"])
        assertEquals("HUNT", doc["activity"])
        assertEquals(listOf("BLACK_BEAR"), doc["species"])
        assertEquals(48.0 to -79.0, doc["lat"] to doc["lng"])
        assertNull(doc["priceFrom"])
        assertEquals(2400, TestGuides.application.toDocument()["priceFrom"])
    }

    @Test
    fun `the email goes to us, with the guide to reply to and every field`() {
        val mail = TestGuides.application.toMail("owner@example.com")
        assertEquals("owner@example.com", mail["to"])
        assertEquals("guide@example.com", mail["replyTo"])
        val message = mail["message"] as Map<*, *>
        assertEquals("New guide application: Pourvoirie Test", message["subject"])
        val text = message["text"] as String
        listOf("Offers: HUNT", "Species: BLACK_BEAR", "Location: 48.0, -79.0", "Price from: 2400", "boreal forest").forEach {
            assertTrue("has $it", it in text)
        }
        val noPrice = TestGuides.application.copy(priceFrom = "").toMail("o@e.com")["message"] as Map<*, *>
        assertTrue("on request" in noPrice["text"] as String)
    }
}
