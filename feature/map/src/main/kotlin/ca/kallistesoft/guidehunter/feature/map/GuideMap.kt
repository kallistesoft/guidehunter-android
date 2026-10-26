package ca.kallistesoft.guidehunter.feature.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import ca.kallistesoft.guidehunter.core.designsystem.theme.DarkPinColors
import ca.kallistesoft.guidehunter.core.designsystem.theme.LightPinColors
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Google Maps with a pin per guide, the mockup's: orange for hunting, teal for fishing, gold for both. It opens on southern
 * Quebec and moves to the user once their location is known (once: not again after a rotation, nor over a pan). The
 * API key comes from the build (README, "Google Maps").
 */
@Composable
internal fun GuideMap(content: MapContent, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current.density
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(GeoPoint.DEFAULT_CENTER.toLatLng(), DEFAULT_ZOOM)
    }
    var centredOnUser by rememberSaveable { mutableStateOf(false) }
    // The cards list the guides on screen: the area is reported each time the camera stops (the padding under the
    // cards excluded). Not before the map is loaded: until it's laid out, its projection is stale (a camera restored
    // after a relaunch would report the default area).
    var loaded by remember { mutableStateOf(false) }
    val onVisibleArea by rememberUpdatedState(content.onVisibleArea)
    LaunchedEffect(camera) {
        snapshotFlow {
            camera.position // re-read once a move settles
            if (!loaded || camera.isMoving) null else camera.projection?.visibleRegion?.latLngBounds
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { onVisibleArea(GeoBounds(it.southwest.toGeoPoint(), it.northeast.toGeoPoint())) }
    }
    LaunchedEffect(content.userLocation) {
        val location = content.userLocation
        if (location != null && !centredOnUser) {
            camera.position = CameraPosition.fromLatLngZoom(location.toLatLng(), USER_ZOOM)
            centredOnUser = true
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = content.userLocation != null),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
        mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        // The guide cards cover the map's bottom: Google's logo and the camera's centre move up above them.
        contentPadding = PaddingValues(bottom = Sizes.guideCardsInset),
        onMapLoaded = { loaded = true },
    ) {
        // One icon per activity, made once per theme: a new descriptor per pass would redraw every marker.
        val icons = remember(dark, density) {
            val colors = if (dark) DarkPinColors else LightPinColors
            GuideActivity.entries.associateWith {
                BitmapDescriptorFactory.fromBitmap(pinBitmap(colors.of(it), colors.outline, density))
            }
        }
        content.guides.forEach { item ->
            key(item.guide.id) {
                Marker(
                    state = rememberUpdatedMarkerState(item.guide.location.toLatLng()),
                    title = item.guide.title,
                    icon = icons.getValue(item.guide.activity),
                    onClick = {
                        content.onSelect(item.guide.id)
                        true // the sheet shows the offer: no info window
                    },
                )
            }
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

private const val DEFAULT_ZOOM = 5f
private const val USER_ZOOM = 6f
