package ca.kallistesoft.guidehunter.feature.becomeguide

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Google's sign-in sheet: an ID token for Firebase Auth. An interface, so tests can stand in for it. */
interface GoogleSignIn {
    /** Shows the sheet over [activity]; the chosen account's ID token, or null if the guide closed it. Throws on failure. */
    suspend fun idToken(activity: Context): String?

    /** Forgets the account chosen, so the next sign-in asks which one. */
    suspend fun signOut(activity: Context)
}

/** The OAuth web client id Firebase gives (`default_web_client_id`, from google-services.json); null without Firebase. */
data class GoogleSignInConfig(val webClientId: String?)

/** Google's sign-in through Android's Credential Manager ("Sign in with Google" button flow). */
class CredentialManagerGoogleSignIn @Inject constructor(
    private val config: GoogleSignInConfig,
) : GoogleSignIn {
    override suspend fun idToken(activity: Context): String? {
        val clientId = config.webClientId ?: throw BackendNotConfiguredException()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Not a Google ID token: ${credential.type}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    override suspend fun signOut(activity: Context) {
        CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
    }
}

/** Public so app tests can swap it for a fake (`@UninstallModules`). */
@Module
@InstallIn(SingletonComponent::class)
interface GoogleSignInModule {
    @Binds
    fun googleSignIn(impl: CredentialManagerGoogleSignIn): GoogleSignIn
}
