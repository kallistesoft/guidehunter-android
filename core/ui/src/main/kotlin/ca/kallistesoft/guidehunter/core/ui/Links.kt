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
    /**
     * Web pages open in a Custom Tab, falling back to any app that can view the link. The address was typed by a
     * guide: only https (http is upgraded, anything else is taken as a host) with a host opens; else false.
     */
    fun open(url: String): Boolean {
        val uri = websiteUri(url) ?: return false
        return try {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // like start(): works from any context
            tab.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            start(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    /** Opens a pre-filled email to [to], an address typed by a guide: encoded, so it can't add recipients or a body. */
    fun email(to: String, subject: String? = null): Boolean {
        // Encoded by hand: some mail apps show '+' literally for spaces.
        val query = subject?.let { "?subject=${Uri.encode(it)}" }.orEmpty()
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(to.trim(), "@")}$query".toUri())
        subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) } // for the mail apps that ignore the query
        return start(intent)
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

/**
 * The https address of a guide's website as typed ("example.com", "http://x.ca/a"), or null when it has no host or
 * another scheme (javascript:, intent:…), which an address typed by a guide must never open.
 */
internal fun websiteUri(url: String): Uri? {
    val trimmed = url.trim()
    val parsed = trimmed.toUri()
    val uri = when (parsed.scheme?.lowercase()) {
        "https" -> parsed
        "http" -> parsed.buildUpon().scheme("https").build()
        null -> "https://$trimmed".toUri()
        else -> return null
    }
    val host = uri.host
    return uri.takeIf { !host.isNullOrBlank() && host.none { c -> c.isWhitespace() } }
}

@Composable
fun rememberLinkOpener(): LinkOpener {
    val context = LocalContext.current
    return remember(context) { LinkOpener(context) }
}
