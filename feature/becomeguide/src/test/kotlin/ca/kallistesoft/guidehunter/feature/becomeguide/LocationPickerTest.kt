package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import ca.kallistesoft.guidehunter.core.designsystem.theme.GuideHunterTheme
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewGroup

/** The picker on a mocked Google map (Google Maps can't draw on the JVM): a tap places the pin, the pin is drawn. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [LocationPickerTest.ShadowMapView::class], instrumentedPackages = ["com.google.android.gms.maps"])
class LocationPickerTest {
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
    private val added = mutableListOf<LatLng>()
    private val clicks = slot<GoogleMap.OnMapClickListener>()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `a tap places the pin, which is then drawn there`() {
        ShadowMapView.ready = map
        mockkStatic(CameraUpdateFactory::class)
        every { CameraUpdateFactory.newCameraPosition(any()) } returns mockk()
        every { map.addMarker(any()) } answers {
            added += firstArg<MarkerOptions>().position
            mockk<Marker>(relaxed = true)
        }
        every { map.setOnMapClickListener(capture(clicks)) } returns Unit
        var location by mutableStateOf<GeoPoint?>(null)

        compose.setContent { GuideHunterTheme { LocationPicker(location, { location = it }) } }
        compose.waitForIdle()
        assertEquals("no pin before a tap", emptyList<LatLng>(), added)

        clicks.captured.onMapClick(LatLng(48.0, -79.0))
        compose.waitForIdle()
        assertEquals(GeoPoint(48.0, -79.0), location)
        assertEquals(listOf(LatLng(48.0, -79.0)), added)
        verify(exactly = 0) { map.animateCamera(any(), any<GoogleMap.CancelableCallback>()) }
    }

    @Test
    fun `a pin placed another way brings the map to it`() {
        ShadowMapView.ready = map
        mockkStatic(CameraUpdateFactory::class)
        every { CameraUpdateFactory.newCameraPosition(any()) } returns mockk()
        val toPin = mockk<CameraUpdate>()
        every { CameraUpdateFactory.newLatLngZoom(LatLng(46.8, -71.2), 11f) } returns toPin
        every { map.animateCamera(any(), any<GoogleMap.CancelableCallback>()) } answers { secondArg<GoogleMap.CancelableCallback?>()?.onFinish() }
        val loaded = slot<GoogleMap.OnMapLoadedCallback>()
        every { map.setOnMapLoadedCallback(capture(loaded)) } returns Unit
        var location by mutableStateOf<GeoPoint?>(null)

        compose.setContent { GuideHunterTheme { LocationPicker(location, { location = it }) } }
        compose.waitForIdle()
        location = GeoPoint(46.8, -71.2) // "Use my location", before the map is up: it waits
        compose.waitForIdle()
        verify(exactly = 0) { map.animateCamera(any(), any<GoogleMap.CancelableCallback>()) }
        loaded.captured.onMapLoaded()
        compose.waitForIdle()
        verify { map.animateCamera(toPin, any<GoogleMap.CancelableCallback>()) }
    }
}
