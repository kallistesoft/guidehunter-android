package ca.kallistesoft.guidehunter.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.kallistesoft.guidehunter.core.designsystem.component.LinkButton
import ca.kallistesoft.guidehunter.core.designsystem.component.PreviewSurface
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `buttons call back, and a disabled one doesn't`() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            PreviewSurface {
                androidx.compose.foundation.layout.Column {
                    PrimaryButton("Send", { clicks += "send" }, icon = Icons.Default.Check)
                    SecondaryButton("Cancel", { clicks += "cancel" })
                    SecondaryButton("Off", { clicks += "off" }, enabled = false, icon = Icons.Default.Check)
                    LinkButton({ clicks += "link" }) { Text("Website") }
                }
            }
        }
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Website").performClick()
        compose.onNodeWithText("Off").assertIsNotEnabled()
        assertEquals(listOf("send", "cancel", "link"), clicks)
    }

    @Test
    @Config(qualifiers = "night")
    fun `the theme follows the system's dark mode`() {
        var primary = Color.Unspecified
        var dark = false
        compose.setContent {
            GuideHunterTheme {
                dark = isSystemInDarkTheme()
                primary = MaterialTheme.colorScheme.primary
            }
        }
        compose.waitForIdle()
        assertEquals(true, dark)
        assertEquals(Color(0xFFF27B2B), primary)
    }

    @Test
    fun `the light theme uses the light blaze orange`() {
        var primary = Color.Unspecified
        compose.setContent { GuideHunterTheme(darkTheme = false) { primary = MaterialTheme.colorScheme.primary } }
        compose.waitForIdle()
        assertEquals(Color(0xFFE8691E), primary)
    }
}
