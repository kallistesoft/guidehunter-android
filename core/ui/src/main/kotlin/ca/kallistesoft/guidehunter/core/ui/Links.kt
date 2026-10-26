package ca.kallistesoft.guidehunter.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** Opens a guide's contact links (website, email, phone); returns false instead of throwing when nothing can. */
class LinkOpener(private val context: Context) {
    /** Web pages open in a Custom Tab, falling back to any app that can view the link. */
    fun open(url: String): Boolean {
        val uri = (if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url").toUri()
        return try {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // like start(): works from any context
            tab.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            start(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    /** Opens a pre-filled email. */
    fun email(to: String, subject: String? = null): Boolean {
        // Encoded by hand: some mail apps show '+' literally for spaces.
        val query = subject?.let { "?subject=${Uri.encode(it)}" }.orEmpty()
        return start(Intent(Intent.ACTION_SENDTO, "mailto:$to$query".toUri()))
    }

    /** Opens the dialer on [phone]; the user still taps Call. */
    fun dial(phone: String): Boolean = start(Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(phone)}".toUri()))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
fun rememberLinkOpener(): LinkOpener {
    val context = LocalContext.current
    return remember(context) { LinkOpener(context) }
}
