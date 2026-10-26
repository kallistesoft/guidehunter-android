package ca.kallistesoft.guidehunter.feature.map

import androidx.compose.ui.test.junit4.createComposeRule
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import androidx.compose.ui.graphics.toArgb
import ca.kallistesoft.guidehunter.core.designsystem.theme.LightPinColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.VisibleRegion
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewGroup

/**
 * The pins, on a mocked Google map: Google Maps can't draw on the JVM, so the map view hands the composable a mock
 * as soon as it asks, and the test sees the markers added and taps one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // the pins are drawn: their pixels are read
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
    fun `a pin per guide, coloured by activity, a tap opens the guide, and the area on screen is reported`() {
        ShadowMapView.ready = map
        mockkStatic(CameraUpdateFactory::class, BitmapDescriptorFactory::class)
        every { CameraUpdateFactory.newCameraPosition(any()) } returns mockk()
        // The icon's fill, read at the pin's body (below its hole).
        val fills = mutableMapOf<BitmapDescriptor, Int>()
        every { BitmapDescriptorFactory.fromBitmap(any()) } answers {
            val bitmap = firstArg<android.graphics.Bitmap>()
            mockk<BitmapDescriptor>().also { fills[it] = bitmap.getPixel(bitmap.width / 2, bitmap.height * 26 / 38) }
        }
        every { map.addMarker(any()) } answers {
            val options = firstArg<MarkerOptions>()
            added += options
            mockk<Marker>(relaxed = true).also { markers[options.title!!] = it }
        }
        every { map.setOnMarkerClickListener(capture(clicks)) } returns Unit
        val selected = mutableListOf<String>()
        val sw = LatLng(45.0, -80.0)
        val ne = LatLng(50.0, -64.0)
        every { map.projection.visibleRegion } returns VisibleRegion(sw, LatLng(45.0, -64.0), LatLng(50.0, -80.0), ne, LatLngBounds(sw, ne))
        val areas = mutableListOf<GeoBounds>()

        compose.setContent {
            GuideHunterTheme {
                GuideMap(MapContent(TestGuides.all.map { GuideItem(it) }, TestGuides.quebecCity, { areas += it }) { selected += it })
            }
        }
        compose.waitForIdle()

        assertEquals(TestGuides.all.map { it.title }, added.map { it.title })
        assertEquals(
            listOf(LightPinColors.hunt, LightPinColors.fish, LightPinColors.both).map { it.toArgb() },
            added.map { fills.getValue(it.icon!!) },
        )
        assertTrue("the sheet shows the offer, not an info window", clicks.captured.onMarkerClick(markers.getValue(TestGuides.salmon.title)))
        assertEquals(listOf("salmon"), selected)
        assertEquals("once the map is ready", listOf(GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -64.0))), areas)
    }
}
