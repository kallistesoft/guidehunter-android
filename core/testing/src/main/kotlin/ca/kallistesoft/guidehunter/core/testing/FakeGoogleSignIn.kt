package ca.kallistesoft.guidehunter.core.testing

import android.content.Context
import ca.kallistesoft.guidehunter.core.auth.GoogleSignIn

/** Google's sheet as a test sets it: hands out [token] (null: the guide closed it), or throws [failure]. */
class FakeGoogleSignIn(var token: String? = FakeAuthRepository.TOKEN) : GoogleSignIn {
    var failure: Exception? = null
    var shown = 0
        private set
    var signedOut = 0
        private set

    override suspend fun idToken(activity: Context): String? {
        shown++
        failure?.let { throw it }
        return token
    }

    override suspend fun signOut(activity: Context) {
        signedOut++
    }
}
