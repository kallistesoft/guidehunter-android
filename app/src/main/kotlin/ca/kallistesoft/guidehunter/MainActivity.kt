package ca.kallistesoft.guidehunter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ca.kallistesoft.guidehunter.core.ads.AdConsent
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity. AppCompat only provides the per-app language API; the UI is all Compose. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var adConsent: AdConsent

    private val app: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the app knows its home (the settings read from disk): no flash of the wrong screen.
        installSplashScreen().setKeepOnScreenCondition { app.home.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ads consent (UMP) on each launch: the form shows only where it's required.
        adConsent.gatherConsent(this)
        setContent {
            GuideHunterTheme {
                // Test tags as resource ids: the baseline profile generator (UiAutomator) finds the screens by them.
                @OptIn(ExperimentalComposeUiApi::class)
                GuideHunterApp(app, Modifier.semantics { testTagsAsResourceId = true })
            }
        }
    }
}
