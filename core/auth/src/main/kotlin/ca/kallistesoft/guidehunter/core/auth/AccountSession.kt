package ca.kallistesoft.guidehunter.core.auth

import android.content.Context
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.model.GuideUser
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The account's actions, the same from the welcome and the settings: Google's sheet, then Firebase. Each shows
 * Google's sheet over [Context], the activity the screen passes, and throws when it fails (offline, not configured).
 */
class AccountSession @Inject constructor(
    private val auth: AuthRepository,
    private val google: GoogleSignIn,
    private val guideAccount: GuideAccountRepository,
    private val settings: SettingsRepository,
) {
    val user: Flow<GuideUser?> = auth.user

    /** Signs in; false if the sheet was closed. */
    suspend fun signIn(activity: Context): Boolean {
        val token = google.idToken(activity) ?: return false
        auth.signIn(token)
        return true
    }

    /** Signs out, back to user mode: guide mode needs an account. */
    suspend fun signOut(activity: Context) {
        settings.setMode(AppMode.USER)
        auth.signOut()
        runCatching { google.signOut(activity) } // only forgets the chosen account: not worth an error
    }

    /**
     * Deletes the guide's offers (off the map too), profile and account, and the app starts over at the welcome.
     * Firebase asks for a recent sign-in first, so Google's sheet shows again; closing it deletes nothing (false).
     */
    suspend fun deleteAccount(activity: Context): Boolean {
        val user = auth.user.first() ?: return false
        val token = google.idToken(activity) ?: return false
        auth.reauthenticate(token)
        guideAccount.deleteAll(user.uid)
        auth.deleteUser()
        settings.reset()
        runCatching { google.signOut(activity) }
        return true
    }
}
