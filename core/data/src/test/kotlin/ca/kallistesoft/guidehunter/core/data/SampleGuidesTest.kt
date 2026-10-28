package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleGuidesTest {
    @Test
    fun `the sample guides show`() = runTest {
        assertEquals(SAMPLE_GUIDES, SampleGuideRepository().guides.first())
    }

    @Test
    fun `without Firebase no one is signed in, and nothing can be saved`() = runTest {
        val auth = UnconfiguredAuthRepository()
        assertNull(auth.user.first())
        auth.signOut()
        val account = UnconfiguredGuideAccountRepository()
        assertNull(account.profile("u").first())
        assertEquals(emptyList<GuideOffer>(), account.offers("u").first())
        assertNull(account.offer("u", "o"))
        val offer = GuideOffer("o", TestGuides.offer)
        val users = UnconfiguredUserAccountRepository()
        val requests = UnconfiguredRequestRepository()
        assertNull(users.contact("u").first())
        assertTrue(requests.sent("u").first().isEmpty())
        assertTrue(requests.received("u").first().isEmpty())
        assertNull(requests.request("o", "u").first())
        val contact = UserContact("Alex", "a@b.ca")
        listOf<suspend () -> Unit>(
            { users.saveContact("u", contact) },
            { users.deleteAll("u") },
            { requests.send("u", TestGuides.bear, null, contact) },
            { requests.cancel("r") },
            { requests.resend("r", null, contact) },
            { requests.approve("r", TestGuides.profile) },
            { requests.reject("r") },
            { requests.markOfferRemoved("u", null) },
            { requests.deleteSent("u") },
            { auth.signIn("t") },
            { auth.reauthenticate("t") },
            { auth.deleteUser() },
            { account.saveProfile("u", TestGuides.profile) },
            { account.saveOffer("u", null, TestGuides.offer, TestGuides.profile) },
            { account.deleteOffer("u", offer) },
            { account.boost("u", "o") },
            { account.deleteAll("u") },
        ).forEach { call ->
            assertTrue(runCatching { call() }.exceptionOrNull() is BackendNotConfiguredException)
        }
    }
}
