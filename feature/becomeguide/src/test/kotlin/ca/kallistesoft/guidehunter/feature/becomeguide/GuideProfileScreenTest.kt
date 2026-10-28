package ca.kallistesoft.guidehunter.feature.becomeguide

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class GuideProfileScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val uid = TestGuides.user.uid
    private val account = FakeGuideAccountRepository()
    private var backs = 0
    private var dones = 0

    private fun str(id: Int) = app.getString(id)

    private fun launch() {
        val viewModel = GuideProfileViewModel(FakeAuthRepository(TestGuides.user), account, SavedStateHandle())
        compose.setContent {
            GuideHunterTheme {
                GuideProfileScreen(onBack = { backs++ }, onDone = { dones++ }, viewModel = viewModel)
            }
        }
    }

    private fun type(tag: String, text: String) = compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    @Test
    fun `a profile is filled in and saved, then the screen closes`() {
        launch()
        compose.onNodeWithText("guide@example.com").assertExists() // the Google email, to start with
        type("field-business-name", "Pourvoirie Test")
        type("field-phone", "819 555 0100")
        type("field-website", "example.com")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(TestGuides.profile.copy(website = "example.com"), account.profiles.value[uid])
        assertEquals(1, dones)
    }

    @Test
    fun `every error shows`() {
        launch()
        compose.onNodeWithTag("field-email").performScrollTo().performTextClearance()
        type("field-phone", "555")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        listOf(R.string.error_business_name, R.string.error_email, R.string.error_phone, R.string.form_has_errors).forEach {
            compose.onNodeWithText(str(it)).performScrollTo()
        }
    }

    @Test
    fun `saving, then a failed save, says so`() {
        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.onWrite = CompletableDeferred()
        account.failure = BackendNotConfiguredException()
        launch()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.saving)).performScrollTo()
        account.onWrite?.complete(Unit)
        compose.onNodeWithText(str(R.string.send_not_configured)).performScrollTo()
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, backs)
        assertEquals(0, dones)
    }
}
