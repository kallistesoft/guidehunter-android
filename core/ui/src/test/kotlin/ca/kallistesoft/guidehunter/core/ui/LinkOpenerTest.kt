package ca.kallistesoft.guidehunter.core.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkOpenerTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val links = LinkOpener(app)

    private fun started(): Intent = shadowOf(app).nextStartedActivity

    @Test
    fun `a website opens over https, with or without its scheme`() {
        assertTrue(links.open("example.com"))
        assertEquals("https://example.com", started().data.toString())
        assertTrue(links.open("http://example.com/a"))
        assertEquals("http://example.com/a", started().data.toString())
    }

    @Test
    fun `an email opens the mail app, with the subject encoded`() {
        assertTrue(links.email("guide@example.com", "Trip found"))
        val intent = started()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:guide@example.com?subject=Trip%20found", intent.data.toString())
        links.email("guide@example.com")
        assertEquals("mailto:guide@example.com", started().data.toString())
    }

    @Test
    fun `a phone number opens the dialer`() {
        assertTrue(links.dial("+1 819 555 0100"))
        val intent = started()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("+1 819 555 0100", intent.data?.schemeSpecificPart)
    }

    @Test
    fun `nothing to handle a link returns false`() {
        shadowOf(app).checkActivities(true)
        assertFalse(links.dial("123"))
        assertFalse(links.open("example.com"))
    }

    @Test
    fun `screens get an opener for their context`() {
        var opener: LinkOpener? = null
        compose.setContent { opener = rememberLinkOpener() }
        assertNotNull(opener)
    }
}
