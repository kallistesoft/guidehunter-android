package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * A small Google map: a tap places the guide's pin there ([onPick]); the pin shows at [location]. A pin placed
 * another way ("Use my location", typed coordinates, an offer opened to edit) brings the map to it, zoomed in.
 */
@Composable
internal fun LocationPicker(location: GeoPoint?, onPick: (GeoPoint) -> Unit, modifier: Modifier = Modifier) {
    val start = location ?: GeoPoint.DEFAULT_CENTER
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(start.latitude, start.longitude), ZOOM)
    }
    // The last tapped point: the map is already there, so it doesn't move.
    var tapped by remember { mutableStateOf<GeoPoint?>(null) }
    // Camera updates can only be made once the map is up (Google Maps initializes CameraUpdateFactory).
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(location, loaded) {
        if (loaded && location != null && location != tapped) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), PIN_ZOOM))
        }
    }
    GoogleMap(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(mapToolbarEnabled = false),
        mapColorScheme = if (isSystemInDarkTheme()) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        onMapLoaded = { loaded = true },
        onMapClick = {
            val point = GeoPoint(it.latitude, it.longitude)
            tapped = point
            onPick(point)
        },
    ) {
        if (location != null) Marker(state = rememberUpdatedMarkerState(LatLng(location.latitude, location.longitude)))
    }
}

private const val ZOOM = 5f

/** Close enough to check the pin is on the right lake or road. */
private const val PIN_ZOOM = 11f
