package ca.kallistesoft.guidehunter.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What most users do first: a cold start, the map with its guides, a guide's offer. The profile precompiles these
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
        // The guide cards (the sample guides without Firebase, the real ones with it).
        device.wait(Until.hasObject(By.res("guide-count")), TIMEOUT)
        device.findObject(By.res("guide-sample-bear"))?.click()
        device.wait(Until.hasObject(By.res("guide-detail")), TIMEOUT)
        device.pressBack()
        // The form.
        device.findObject(By.res("become-guide"))?.click()
        device.wait(Until.hasObject(By.res("field-business-name")), TIMEOUT)
        device.pressBack()
    }

    private companion object {
        const val PACKAGE = "ca.kallistesoft.guidehunter"
        const val TIMEOUT = 10_000L
    }
}
