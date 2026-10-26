package ca.kallistesoft.guidehunter.feature.map

import androidx.compose.ui.test.junit4.createComposeRule
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewGroup

/**
 * The pins, on a mocked Google map: Google Maps can't draw on the JVM, so the map view hands the composable a mock
 * as soon as it asks, and the test sees the markers added and taps one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [GuideMapTest.ShadowMapView::class], instrumentedPackages = ["com.google.android.gms.maps"])
class GuideMapTest {
    /** A map view whose map is ready at once: [ready]. */
    @Implements(MapView::class)
    class ShadowMapView : ShadowViewGroup() {
        @Implementation
        fun getMapAsync(callback: OnMapReadyCallback) = callback.onMapReady(ready)

        companion object {
            lateinit var ready: GoogleMap
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val map = mockk<GoogleMap>(relaxed = true)
    private val added = mutableListOf<MarkerOptions>()
    private val markers = mutableMapOf<String, Marker>()
    private val clicks = slot<GoogleMap.OnMarkerClickListener>()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `a pin per guide, coloured by activity, and a tap opens the guide`() {
        ShadowMapView.ready = map
        mockkStatic(CameraUpdateFactory::class, BitmapDescriptorFactory::class)
        every { CameraUpdateFactory.newCameraPosition(any()) } returns mockk()
        val hues = mutableMapOf<BitmapDescriptor, Float>()
        every { BitmapDescriptorFactory.defaultMarker(any()) } answers { mockk<BitmapDescriptor>().also { hues[it] = firstArg() } }
        every { map.addMarker(any()) } answers {
            val options = firstArg<MarkerOptions>()
            added += options
            mockk<Marker>(relaxed = true).also { markers[options.title!!] = it }
        }
        every { map.setOnMarkerClickListener(capture(clicks)) } returns Unit
        val selected = mutableListOf<String>()

        compose.setContent {
            GuideHunterTheme {
                GuideMap(MapContent(TestGuides.all.map { GuideItem(it) }, TestGuides.quebecCity) { selected += it })
            }
        }
        compose.waitForIdle()

        assertEquals(TestGuides.all.map { it.title }, added.map { it.title })
        assertEquals(
            listOf(BitmapDescriptorFactory.HUE_ORANGE, BitmapDescriptorFactory.HUE_GREEN, BitmapDescriptorFactory.HUE_YELLOW),
            added.map { hues.getValue(it.icon!!) },
        )
        assertTrue("the sheet shows the offer, not an info window", clicks.captured.onMarkerClick(markers.getValue(TestGuides.salmon.title)))
        assertEquals(listOf("salmon"), selected)
    }
}
