package ca.kallistesoft.guidehunter.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What most users do first: a cold start, the welcome ("Later"), the map with its guides, a guide's offer. The profile precompiles these
 * paths, so the first launch after an install doesn't run them interpreted.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // A first launch shows the welcome; later runs go straight to the map.
        device.findObject(By.res("later"))?.click()
        // The guide cards (the sample guides without Firebase, the real ones with it).
        device.wait(Until.hasObject(By.res("guide-count")), TIMEOUT)
        device.findObject(By.res("guide-sample-bear"))?.click()
        device.wait(Until.hasObject(By.res("guide-detail")), TIMEOUT)
        device.pressBack()
        // The settings.
        device.findObject(By.res("settings"))?.click()
        device.wait(Until.hasObject(By.res("settings-screen")), TIMEOUT)
        device.pressBack()
    }

    private companion object {
        const val PACKAGE = "ca.kallistesoft.guidehunter"
        const val TIMEOUT = 10_000L
    }
}
