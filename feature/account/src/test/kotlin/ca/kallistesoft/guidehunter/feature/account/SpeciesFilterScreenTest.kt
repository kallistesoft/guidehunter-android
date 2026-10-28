package ca.kallistesoft.guidehunter.feature.account

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.AppSettings
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class SpeciesFilterScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val settings = FakeSettingsRepository(AppSettings(onboarded = true))
    private var backs = 0

    private fun launch(discipline: Discipline? = null) {
        val viewModel = SpeciesFilterViewModel(discipline, settings)
        compose.setContent {
            GuideHunterTheme {
                SpeciesFilterScreen(discipline, onBack = { backs++ }, viewModel = viewModel)
            }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `from settings, both disciplines under headings that open and close`() {
        launch()
        compose.onNodeWithText(app.getString(R.string.species_filter_note)).assertExists()
        compose.onNodeWithTag("species-any").assertIsOn()
        compose.onNodeWithTag("species-group-HUNTING").assertExists()
        compose.onNodeWithTag("species-WALLEYE").assertExists()
        click("species-group-FISHING")
        compose.onNodeWithTag("species-WALLEYE").assertDoesNotExist()
        compose.onNodeWithTag("species-BLACK_BEAR").assertExists()
        click("species-group-FISHING")
        compose.onNodeWithTag("species-WALLEYE").assertExists()
    }

    @Test
    fun `each tap is saved, clear and any species empty the choice`() {
        launch()
        compose.onNodeWithTag("species-clear").assertIsNotEnabled()
        click("species-MOOSE")
        click("species-WALLEYE")
        assertEquals(setOf(Species.MOOSE, Species.WALLEYE), settings.settings.value.speciesFilter)
        compose.onNodeWithTag("species-MOOSE").assertIsOn()
        compose.onNodeWithTag("species-any").assertIsOff()
        compose.onNodeWithTag("species-group-HUNTING").assertTextContains("1/15")
        click("species-MOOSE")
        assertEquals(setOf(Species.WALLEYE), settings.settings.value.speciesFilter)

        compose.onNodeWithTag("species-clear").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(emptySet<Species>(), settings.settings.value.speciesFilter)

        click("species-PHEASANT")
        click("species-any")
        assertEquals(emptySet<Species>(), settings.settings.value.speciesFilter)
        compose.onNodeWithTag("species-any").assertIsOn()
    }

    @Test
    fun `from the map, only its discipline, without headings`() {
        launch(Discipline.HUNTING)
        compose.onNodeWithTag("species-BLACK_BEAR").assertExists()
        compose.onNodeWithTag("species-WALLEYE").assertDoesNotExist()
        compose.onNodeWithTag("species-group-HUNTING").assertDoesNotExist()
    }

    @Test
    fun `show on the map and back both go back`() {
        launch()
        compose.onNodeWithTag("species-done").performClick()
        compose.onNodeWithTag("back").performClick()
        assertEquals(2, backs)
    }
}
