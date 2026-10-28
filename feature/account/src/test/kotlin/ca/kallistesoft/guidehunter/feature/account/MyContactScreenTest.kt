package ca.kallistesoft.guidehunter.feature.account

import android.app.Application
import androidx.compose.ui.test.assertTextContains
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
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
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
class MyContactScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val users = FakeUserAccountRepository()
    private var backs = 0
    private var done = 0

    private fun launch() {
        val viewModel = MyContactViewModel(FakeAuthRepository(TestGuides.user), users, SavedStateHandle())
        compose.setContent { GuideHunterTheme { MyContactScreen(onBack = { backs++ }, onDone = { done++ }, viewModel = viewModel) } }
    }

    @Test
    fun `the email is the Google account's, the note says sent requests keep their copy`() {
        launch()
        compose.onNodeWithTag("field-email").assertTextContains(TestGuides.user.email)
        compose.onNodeWithText(app.getString(R.string.email_from_google)).assertExists()
        compose.onNodeWithText(app.getString(R.string.my_contact_copy_note)).performScrollTo()
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `errors show, and a valid contact is saved and closes`() {
        launch()
        compose.onNodeWithTag("field-name").performTextClearance()
        compose.onNodeWithTag("field-phone").performTextInput("555")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.error_name)).assertExists()
        compose.onNodeWithText(app.getString(R.string.error_phone)).assertExists()
        compose.onNodeWithTag("field-name").performTextInput("Alex")
        compose.onNodeWithTag("field-phone").performTextInput(" 0199 123")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Alex", users.contacts.value[TestGuides.user.uid]?.name)
        assertEquals(1, done)
    }

    @Test
    fun `a failed save says so`() {
        users.failure = IllegalStateException("offline")
        launch()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.save_failed)).assertExists()
        users.failure = BackendNotConfiguredException()
        compose.onNodeWithTag("field-name").performTextInput("x")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.save_not_configured)).assertExists()
    }

    @Test
    fun `while saving the button waits`() {
        users.onWrite = CompletableDeferred()
        launch()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.saving)).assertExists()
        users.onWrite?.complete(Unit)
        compose.waitForIdle()
        assertEquals(1, done)
    }
}
