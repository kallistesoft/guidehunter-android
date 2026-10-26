package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/** A small Google map: a tap places the guide's pin there ([onPick]); the pin shows at [location]. */
@Composable
internal fun LocationPicker(location: GeoPoint?, onPick: (GeoPoint) -> Unit, modifier: Modifier = Modifier) {
    val start = location ?: GeoPoint.DEFAULT_CENTER
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(start.latitude, start.longitude), ZOOM)
    }
    GoogleMap(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(mapToolbarEnabled = false),
        mapColorScheme = if (isSystemInDarkTheme()) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        onMapClick = { onPick(GeoPoint(it.latitude, it.longitude)) },
    ) {
        if (location != null) Marker(state = rememberUpdatedMarkerState(LatLng(location.latitude, location.longitude)))
    }
}

private const val ZOOM = 5f
