package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the picker draws without a pin, then with one`() {
        var location by mutableStateOf<GeoPoint?>(null)
        compose.setContent { GuideHunterTheme { LocationPicker(location, { location = it }) } }
        compose.waitForIdle()
        location = GeoPoint(48.0, -79.0)
        compose.waitForIdle()
    }
}
