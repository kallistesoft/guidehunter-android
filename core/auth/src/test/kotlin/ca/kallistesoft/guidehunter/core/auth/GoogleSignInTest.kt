package ca.kallistesoft.guidehunter.core.auth

import android.content.Context
import android.os.Bundle
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Google's sign-in through a mocked Credential Manager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleSignInTest {
    private val activity: Context = RuntimeEnvironment.getApplication()
    private val manager = mockk<CredentialManager>()
    private val signIn = CredentialManagerGoogleSignIn(GoogleSignInConfig("web-client"))

    @Before
    fun setUp() {
        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns manager
    }

    @After
    fun tearDown() = unmockkAll()

    private companion object {
        /** A token of the right shape (header.payload.signature, base64url): the library checks it, not its signature. */
        val JWT = listOf("""{"alg":"RS256"}""", """{"sub":"1","email":"guide@example.com"}""", "sig")
            .joinToString(".") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
    }

    @Test
    fun `the chosen account's ID token, asked for the app's web client`() = runTest {
        val token = GoogleIdTokenCredential.Builder().setId("guide@example.com").setIdToken(JWT).build()
        val request = slot<GetCredentialRequest>()
        coEvery { manager.getCredential(activity, capture(request)) } returns
            androidx.credentials.GetCredentialResponse(CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, token.data))
        assertEquals(JWT, signIn.idToken(activity))
        assertEquals("web-client", (request.captured.credentialOptions.single() as GetSignInWithGoogleOption).serverClientId)
    }

    @Test
    fun `closing the sheet is no token`() = runTest {
        coEvery { manager.getCredential(activity, any<GetCredentialRequest>()) } throws GetCredentialCancellationException()
        assertNull(signIn.idToken(activity))
    }

    @Test
    fun `anything but a Google token is an error`() = runTest {
        coEvery { manager.getCredential(activity, any<GetCredentialRequest>()) } returns
            androidx.credentials.GetCredentialResponse(PasswordCredential("id", "pw"))
        assertTrue(runCatching { signIn.idToken(activity) }.exceptionOrNull() is IllegalStateException)
        coEvery { manager.getCredential(activity, any<GetCredentialRequest>()) } returns
            androidx.credentials.GetCredentialResponse(CustomCredential("other", Bundle()))
        assertTrue(runCatching { signIn.idToken(activity) }.exceptionOrNull() is IllegalStateException)
    }

    @Test(expected = BackendNotConfiguredException::class)
    fun `without Firebase there's no client to sign in to`() = runTest {
        CredentialManagerGoogleSignIn(GoogleSignInConfig(null)).idToken(activity)
    }

    @Test
    fun `sign out forgets the chosen account`() = runTest {
        coEvery { manager.clearCredentialState(any<ClearCredentialStateRequest>()) } returns Unit
        signIn.signOut(activity)
        coVerify { manager.clearCredentialState(any<ClearCredentialStateRequest>()) }
    }
}
