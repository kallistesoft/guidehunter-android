package ca.kallistesoft.guidehunter.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.kallistesoft.guidehunter.core.model.Trip
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DateDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2027, 4, 15)
    private val events = mutableListOf<Any>()

    @Test
    fun `a day is picked, OK waits for one`() {
        compose.setContent { MaterialTheme { DateDialog(today, { events += it }, { events += "dismissed" }) } }
        compose.onNodeWithTag("date-ok").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<Any>("dismissed"), events)
    }

    @Test
    fun `a chosen day comes back as a date`() {
        compose.setContent { MaterialTheme { DateDialog(today, { events += it }, {}, initial = LocalDate(2027, 5, 20)) } }
        compose.onNodeWithTag("date-ok").performClick()
        assertEquals(listOf<Any>(LocalDate(2027, 5, 20)), events)
    }

    @Test
    fun `a range comes back as a trip, and can be cleared`() {
        val may = Trip(LocalDate(2027, 5, 18), LocalDate(2027, 5, 31))
        compose.setContent { MaterialTheme { DateRangeDialog(today, { events += it }, {}, initial = may, onClear = { events += "cleared" }) } }
        compose.onNodeWithTag("dates-ok").performClick()
        compose.onNodeWithTag("dates-clear").performClick()
        assertEquals(listOf<Any>(may, "cleared"), events)
    }

    @Test
    fun `one day is a range too, and without clear there's cancel`() {
        val day = Trip(LocalDate(2027, 7, 2), LocalDate(2027, 7, 2))
        compose.setContent { MaterialTheme { DateRangeDialog(today, { events += it }, { events += "dismissed" }, initial = Trip(LocalDate(2027, 7, 2))) } }
        compose.onNodeWithTag("dates-ok").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<Any>(day, "dismissed"), events)
    }

    @Test
    fun `nothing chosen, OK waits`() {
        compose.setContent { MaterialTheme { DateRangeDialog(today, { events += it }, {}) } }
        compose.onNodeWithTag("dates-ok").assertIsNotEnabled()
    }
}
