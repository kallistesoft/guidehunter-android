package ca.kallistesoft.guidehunter.core.auth

import android.content.Context
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSessionTest {
    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository()
    private val google = FakeGoogleSignIn()
    private val requests = FakeRequestRepository()
    private val account = FakeGuideAccountRepository(requests)
    private val users = FakeUserAccountRepository()
    private val settings = FakeSettingsRepository(AppSettings(onboarded = true, mode = AppMode.GUIDE))
    private val activity = mockk<Context>()
    private val session = AccountSession(auth, google, account, users, requests, settings)

    @Test
    fun `sign in with Google's token, or nothing when the sheet is closed`() = runTest {
        google.token = null
        assertFalse(session.signIn(activity))
        assertNull(session.user.first())

        google.token = FakeAuthRepository.TOKEN
        assertTrue(session.signIn(activity))
        assertEquals(TestGuides.user, session.user.first())
    }

    @Test
    fun `sign out goes back to user mode and forgets the Google account`() = runTest {
        auth.user.value = TestGuides.user
        session.signOut(activity)
        assertNull(session.user.first())
        assertEquals(AppSettings(onboarded = true, mode = AppMode.USER), settings.settings.value)
        assertEquals(1, google.signedOut)
    }

    @Test
    fun `deleting the account confirms it with Google, deletes everything and starts over`() = runTest {
        auth.user.value = TestGuides.user
        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.give(uid, GuideOffer("o1", TestGuides.offer))
        users.contacts.value = mapOf(uid to TestGuides.userContact, "other" to TestGuides.userContact)
        val received = TestGuides.request.copy(id = "o1_u2", offerId = "o1", guideUid = uid, userUid = "u2")
        val sentByOther = TestGuides.request.copy(id = "bear_u3", userUid = "u3")
        requests.give(TestGuides.request, received, sentByOther)
        assertTrue(session.deleteAccount(activity))
        assertEquals("their contact", setOf("other"), users.contacts.value.keys)
        assertEquals("the requests they sent", setOf("o1_u2", "bear_u3"), requests.requests.value.keys)
        assertEquals("the ones they received closed", RequestStatus.OFFER_REMOVED, requests.requests.value.getValue("o1_u2").status)
        assertEquals(listOf(FakeAuthRepository.TOKEN), auth.reauthenticated)
        assertTrue(auth.deleted)
        assertTrue(account.offersByUser.value.isEmpty())
        assertTrue(account.profiles.value.isEmpty())
        assertEquals(AppSettings(), settings.settings.value)
        assertEquals(1, google.signedOut)
    }

    @Test
    fun `closing Google's sheet, or being signed out, deletes nothing`() = runTest {
        assertFalse(session.deleteAccount(activity))
        assertEquals(0, google.shown)

        auth.user.value = TestGuides.user
        google.token = null
        assertFalse(session.deleteAccount(activity))
        assertFalse(auth.deleted)
        assertTrue(settings.settings.value.onboarded)
    }
}
