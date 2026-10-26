package ca.kallistesoft.guidehunter.core.ads

import android.app.Activity
import android.os.Bundle
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ResumedActivityTest {
    private val first = mockk<Activity>()
    private val second = mockk<Activity>()
    private val resumed = ResumedActivity()

    @Test
    fun `the resumed activity is known until it pauses`() {
        assertNull(resumed.get())
        resumed.onActivityCreated(first, null)
        resumed.onActivityStarted(first)
        resumed.onActivityResumed(first)
        assertSame(first, resumed.get())

        resumed.onActivityPaused(first)
        resumed.onActivitySaveInstanceState(first, Bundle())
        resumed.onActivityStopped(first)
        resumed.onActivityDestroyed(first)
        assertNull(resumed.get())
    }

    @Test
    fun `an activity pausing after the next one resumed doesn't clear it`() {
        resumed.onActivityResumed(first)
        resumed.onActivityResumed(second)
        resumed.onActivityPaused(first)
        assertSame(second, resumed.get())
    }
}
