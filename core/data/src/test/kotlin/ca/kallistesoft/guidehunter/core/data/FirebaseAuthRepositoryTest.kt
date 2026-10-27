package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GuideUser
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Google sign-in through a mocked Firebase Auth. */
class FirebaseAuthRepositoryTest {
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val credential = mockk<AuthCredential>()
    private val firebaseUser = mockk<FirebaseUser> {
        every { uid } returns "u1"
        every { email } returns "guide@example.com"
        every { displayName } returns null
    }

    @Before
    fun setUp() {
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential("token", null) } returns credential
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `the user follows Firebase's sign-ins and sign-outs`() = runTest {
        val listener = slot<FirebaseAuth.AuthStateListener>()
        every { auth.addAuthStateListener(capture(listener)) } returns Unit
        val received = mutableListOf<GuideUser?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { FirebaseAuthRepository(auth).user.toList(received) }
        every { auth.currentUser } returns null
        listener.captured.onAuthStateChanged(auth)
        every { auth.currentUser } returns firebaseUser
        listener.captured.onAuthStateChanged(auth)
        listener.captured.onAuthStateChanged(auth) // a token refresh: same user, not sent again
        job.cancel()
        assertEquals(listOf(null, GuideUser("u1", "guide@example.com", "")), received)
        verify { auth.removeAuthStateListener(listener.captured) }
    }

    @Test
    fun `sign in, prove it again, sign out and delete`() = runTest {
        every { auth.signInWithCredential(credential) } returns Tasks.forResult(mockk())
        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.reauthenticate(credential) } returns Tasks.forResult(null)
        every { firebaseUser.delete() } returns Tasks.forResult(null)
        val repository = FirebaseAuthRepository(auth)

        repository.signIn("token")
        repository.reauthenticate("token")
        repository.signOut()
        repository.deleteUser()

        verify { auth.signInWithCredential(credential) }
        verify { firebaseUser.reauthenticate(credential) }
        verify { auth.signOut() }
        verify { firebaseUser.delete() }
    }

    @Test(expected = IllegalStateException::class)
    fun `deleting needs someone signed in`() = runTest {
        every { auth.currentUser } returns null
        FirebaseAuthRepository(auth).deleteUser()
    }
}
