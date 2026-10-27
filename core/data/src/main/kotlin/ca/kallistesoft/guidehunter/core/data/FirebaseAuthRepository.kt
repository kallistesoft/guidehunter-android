package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GuideUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Google sign-in through Firebase Auth: the Google ID token becomes a Firebase account, kept across launches. */
internal class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {
    override val user: Flow<GuideUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toGuideUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(googleIdToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
    }

    override suspend fun reauthenticate(googleIdToken: String) {
        signedIn().reauthenticate(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
    }

    override suspend fun signOut() = auth.signOut()

    override suspend fun deleteUser() {
        signedIn().delete().await()
    }

    private fun signedIn(): FirebaseUser = checkNotNull(auth.currentUser) { "Not signed in" }
}

internal fun FirebaseUser.toGuideUser() = GuideUser(uid = uid, email = email.orEmpty(), name = displayName.orEmpty())
