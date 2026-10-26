package ca.kallistesoft.guidehunter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ca.kallistesoft.guidehunter.core.ads.AdConsent
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity. AppCompat only provides the per-app language API; the UI is all Compose. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var adConsent: AdConsent

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ads consent (UMP) on each launch: the form shows only where it's required.
        adConsent.gatherConsent(this)
        setContent {
            GuideHunterTheme {
                GuideHunterApp()
            }
        }
    }
}
