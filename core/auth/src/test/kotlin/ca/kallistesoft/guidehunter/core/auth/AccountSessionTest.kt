package ca.kallistesoft.guidehunter.core.auth

import android.content.Context
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGoogleSignIn
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
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
    private val account = FakeGuideAccountRepository()
    private val settings = FakeSettingsRepository(AppSettings(onboarded = true, mode = AppMode.GUIDE))
    private val activity = mockk<Context>()
    private val session = AccountSession(auth, google, account, settings)

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
        assertTrue(session.deleteAccount(activity))
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
