package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleGuidesTest {
    @Test
    fun `every sample guide has a contact`() = runTest {
        val repository = SampleGuideRepository()
        val guides = repository.guides.first()
        assertEquals(SAMPLE_GUIDES, guides)
        assertTrue(guides.all { repository.contact(it.id) != null })
        assertNull(repository.contact("unknown"))
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
        listOf<suspend () -> Unit>(
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
