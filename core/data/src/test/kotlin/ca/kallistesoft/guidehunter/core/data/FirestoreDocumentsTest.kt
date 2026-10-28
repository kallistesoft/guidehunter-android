package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
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
    fun `a guide document carries its boost, and a boost writes only that field`() {
        val until = Instant.parse("2026-10-31T12:00:00.5Z")
        val boost = boostUpdate(until)
        assertEquals(mapOf("boostedUntil" to Timestamp(until.epochSeconds, 500_000_000)), boost)
        assertEquals(until, guideFrom("g1", document + boost)!!.boostedUntil)
        assertNull(guideFrom("g1", document + ("boostedUntil" to "soon"))!!.boostedUntil)
        assertEquals(mapOf("a" to until), boostsFrom(listOf("a" to document + boost, "b" to document)))
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
    fun `a published guide carries its owner`() {
        val document = mapOf("title" to "T", "activity" to "HUNT", "lat" to 1.0, "lng" to 2.0)
        assertEquals("u1", guideFrom("g1", document + ("ownerUid" to "u1"))?.ownerUid)
        assertEquals("", guideFrom("g1", document)?.ownerUid)
    }

    @Test
    fun `a user's contact is saved trimmed, and read back`() {
        val doc = UserContact(" Alex ", "a@b.ca", " 418 555 0199").toDocument()
        assertEquals(mapOf("name" to "Alex", "email" to "a@b.ca", "phone" to "418 555 0199"), doc)
        assertEquals(UserContact("Alex", "a@b.ca", "418 555 0199"), userContactFrom(doc))
        assertNull(userContactFrom(null))
        assertEquals(UserContact(), userContactFrom(emptyMap()))
    }

    private val alex = UserContact("Alex", "a@b.ca", "418 555 0199")
    private val trip = Trip(LocalDate(2027, 5, 18), LocalDate(2027, 5, 21))

    @Test
    fun `a new request is pending, with the offer's guide and title, the trip as dates and a copy of the contact`() {
        val doc = requestDocument("u2", TestGuides.bear.copy(ownerUid = "g1"), trip, alex.copy(name = " Alex "))
        assertEquals("bear", doc["offerId"])
        assertEquals("Bear hunt", doc["offerTitle"])
        assertEquals("g1", doc["guideUid"])
        assertEquals("u2", doc["userUid"])
        assertEquals("PENDING", doc["status"])
        assertEquals("2027-05-18", doc["tripStart"])
        assertEquals("2027-05-21", doc["tripEnd"])
        assertNull(doc["tripSlot"])
        assertEquals("Alex", doc["userName"])
        assertEquals("a@b.ca", doc["userEmail"])
        assertEquals("418 555 0199", doc["userPhone"])
        assertTrue(doc["createdAt"] is FieldValue)
        assertTrue(doc["updatedAt"] is FieldValue)
        assertNull(resendFields(null, alex)["tripStart"])
        assertEquals("AM", resendFields(Trip(trip.start, slot = Slot.MORNING), alex)["tripSlot"])
        assertEquals("PM", resendFields(Trip(trip.start, slot = Slot.AFTERNOON), alex)["tripSlot"])
    }

    @Test
    fun `accepting gives the guide's contact`() {
        val fields = approveFields(TestGuides.profile.copy(website = "x.ca "))
        assertEquals("APPROVED", fields["status"])
        assertEquals("Pourvoirie Test", fields["guideName"])
        assertEquals("guide@example.com", fields["guideEmail"])
        assertEquals("819 555 0100", fields["guidePhone"])
        assertEquals("x.ca", fields["guideWebsite"])
        assertEquals("REJECTED", statusFields(RequestStatus.REJECTED)["status"])
    }

    @Test
    fun `a request is read back, the guide's contact only once accepted`() {
        val sent = requestDocument("u2", TestGuides.bear.copy(ownerUid = "g1"), Trip(trip.start, slot = Slot.AFTERNOON), alex) +
            mapOf("createdAt" to Timestamp(1_000, 0), "updatedAt" to Timestamp(2_000, 0))
        val request = requestFrom("bear_u2", sent, now = 5)!!
        assertEquals(
            InterestRequest(
                id = "bear_u2",
                offerId = "bear",
                offerTitle = "Bear hunt",
                trip = Trip(trip.start, trip.start, Slot.AFTERNOON),
                guideUid = "g1",
                userUid = "u2",
                user = alex,
                status = RequestStatus.PENDING,
                createdAt = 1_000_000,
                updatedAt = 2_000_000,
            ),
            request,
        )
        val guide = mapOf("guideName" to "Camp", "guidePhone" to "819", "guideEmail" to "c@d.ca", "guideWebsite" to "c.ca")
        assertNull("pending: not given yet", requestFrom("bear_u2", sent + guide)?.guideContact)
        assertEquals(GuideContact("Camp", "819", "c@d.ca", "c.ca"), requestFrom("bear_u2", sent + guide + ("status" to "APPROVED"))?.guideContact)
        assertNull(requestFrom("bear_u2", sent + ("status" to "APPROVED"))?.guideContact)
        assertEquals("a time not back from the server reads as now", 5L, requestFrom("x", sent - "updatedAt", now = 5)?.updatedAt)
        assertNull("no trip", requestFrom("x", sent - "tripStart")?.trip)
        assertEquals("a day", Trip(trip.start), requestFrom("x", sent - "tripEnd" - "tripSlot")?.trip)
        listOf("offerId", "guideUid", "userUid", "status").forEach { assertNull(it, requestFrom("x", sent - it)) }
        assertNull(requestFrom("x", sent + ("status" to "LOST")))
        assertEquals("", requestFrom("x", mapOf("offerId" to "o", "guideUid" to "g", "userUid" to "u", "status" to "PENDING"))?.offerTitle)
    }

    @Test
    fun `requests come the latest change first, unreadable ones left out`() {
        val base = mapOf("offerId" to "o", "guideUid" to "g", "userUid" to "u", "status" to "PENDING")
        val requests = requestsFrom(
            listOf(
                "old" to base + ("updatedAt" to Timestamp(1, 0)),
                "bad" to emptyMap(),
                "new" to base + ("updatedAt" to Timestamp(9, 0)),
            ),
        )
        assertEquals(listOf("new", "old"), requests.map { it.id })
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
            "Duration: 7 days", "Departures: 2027-05-01",
            "Phone: 819 555 0100", "approve-offer.py <project> o1 (guide u1)",
        ).forEach { assertTrue("has $it", it in text) }

        val edited = offerMail("o@e.com", "o1", "u1", TestGuides.offer.copy(priceFrom = ""), TestGuides.profile, edited = true)
        val editedMessage = edited["message"] as Map<*, *>
        assertTrue((editedMessage["subject"] as String).startsWith("Offer edited: "))
        assertTrue("on request" in editedMessage["text"] as String)

        val salmon = OfferDraft(
            schedule = Schedule(TripLength.HalfDay, listOf(Departure(LocalDate(2027, 6, 10), Slot.AFTERNOON)), everyYear = true),
        )
        val salmonText = (offerMail("o@e.com", "o1", "u1", salmon, TestGuides.profile, false)["message"] as Map<*, *>)["text"] as String
        assertTrue("Duration: half day" in salmonText)
        assertTrue("Departures: 2027-06-10/PM (every year)" in salmonText)
        assertTrue("Duration: not given" in (offerMail("o@e.com", "o1", "u1", OfferDraft(), TestGuides.profile, false)["message"] as Map<*, *>)["text"] as String)

        val long = offerMail("o@e.com", "o1", "u1", TestGuides.offer.copy(title = "x".repeat(400)), TestGuides.profile, false)
        assertEquals(300, ((long["message"] as Map<*, *>)["subject"] as String).length)
    }

    @Test
    fun `a schedule is stored readable and read back, unreadable departures dropped`() {
        val half = Schedule(
            TripLength.HalfDay,
            listOf(Departure(LocalDate(2027, 6, 10), Slot.AFTERNOON), Departure(LocalDate(2027, 6, 10), Slot.MORNING)),
            everyYear = true,
        )
        val fields = half.toFields()
        assertEquals("HALF_DAY", fields["tripLength"])
        assertEquals(listOf("2027-06-10/AM", "2027-06-10/PM"), fields["departures"])
        assertEquals(true, fields["everyYear"])
        assertEquals(half.copy(departures = half.departures.sorted()), scheduleFrom(fields))

        val week = Schedule(TripLength.Days(7), listOf(Departure(LocalDate(2027, 5, 1))))
        assertEquals(week, scheduleFrom(week.toFields() + ("tripLength" to 7L)))
        assertEquals(
            Schedule(departures = listOf(Departure(LocalDate(2027, 5, 1)))),
            scheduleFrom(mapOf("tripLength" to "FOREVER", "departures" to listOf("2027-05-01", "May 8", "2027-05-15/NOON", 3))),
        )
        assertEquals(Schedule(), scheduleFrom(Schedule().toFields()))
        assertNull(Schedule().toFields()["tripLength"])
    }

    @Test
    fun `a guide document carries its schedule`() {
        val data = mapOf("title" to "Salmon", "activity" to "FISH", "lat" to 1.0, "lng" to 2.0, "tripLength" to "HALF_DAY", "departures" to listOf("2027-06-10/AM"))
        assertEquals(Schedule(TripLength.HalfDay, listOf(Departure(LocalDate(2027, 6, 10), Slot.MORNING))), guideFrom("g", data)!!.schedule)
    }
}
