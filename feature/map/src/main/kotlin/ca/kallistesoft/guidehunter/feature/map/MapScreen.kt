package ca.kallistesoft.guidehunter.feature.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideFilter
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.ui.DateRangeDialog
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.dateRange
import ca.kallistesoft.guidehunter.core.ui.label
import kotlinx.datetime.LocalDate

/**
 * What the map draws: the guides to pin, the [featured] ones highlighted and on top, and the user's location; what it
 * reports: the area on screen once the camera stops, and a tap on a pin.
 */
data class MapContent(
    val guides: List<GuideItem>,
    val userLocation: GeoPoint?,
    val onVisibleArea: (GeoBounds) -> Unit = {},
    val featured: Set<String> = emptySet(),
    val onSelect: (String) -> Unit,
)

/**
 * The home screen: a map of the guides, filters above it and the guides' cards below. A pin or a card opens the
 * guide's offer. [map] draws the map (Google Maps); tests pass a stand-in, as Google Maps can't draw on the JVM.
 */
@Composable
fun MapScreen(
    onSettings: () -> Unit,
    onSpeciesFilter: (Discipline?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    map: @Composable (MapContent, Modifier) -> Unit = { content, mapModifier -> GuideMap(content, mapModifier) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The location permission, in its three states: granted (read the location), denied once (explain, then ask
    // again), denied for good (only Settings can turn it on).
    var rationale by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> viewModel.refreshLocation()
            context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, LOCATION_PERMISSION) } == true ->
                rationale = true
            else -> openAppSettings(context)
        }
    }

    MapContent(
        state = state,
        onSettings = onSettings,
        onLocate = {
            if (hasLocationPermission(context)) viewModel.refreshLocation() else permission.launch(LOCATION_PERMISSION)
        },
        onDiscipline = viewModel::chooseDiscipline,
        onSpeciesFilter = onSpeciesFilter,
        onDates = viewModel::chooseDates,
        onAnyDates = viewModel::clearDates,
        onTrip = viewModel::chooseTrip,
        onVisibleArea = viewModel::showArea,
        onSelect = viewModel::select,
        onDismiss = viewModel::dismiss,
        onUnlock = viewModel::unlock,
        map = map,
        modifier = modifier,
    )
    if (rationale) {
        LocationRationale(
            onAllow = {
                rationale = false
                permission.launch(LOCATION_PERMISSION)
            },
            onDismiss = { rationale = false },
        )
    }
}

/** Why the app asks for the location, shown after a first "Don't allow". */
@Composable
private fun LocationRationale(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_rationale_title)) },
        text = { Text(stringResource(R.string.location_rationale)) },
        confirmButton = {
            TextButton(onClick = onAllow, modifier = Modifier.testTag("rationale-allow")) {
                Text(stringResource(R.string.location_rationale_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("rationale-not-now")) {
                Text(stringResource(R.string.location_rationale_not_now))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapContent(
    state: MapUiState,
    onSettings: () -> Unit,
    onLocate: () -> Unit,
    onDiscipline: (Discipline?) -> Unit,
    onSpeciesFilter: (Discipline?) -> Unit,
    onDates: (Trip) -> Unit,
    onAnyDates: () -> Unit,
    onTrip: (Trip) -> Unit,
    onVisibleArea: (GeoBounds) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    map: @Composable (MapContent, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.map_title), style = GhTextStyles.brand) },
                navigationIcon = {
                    IconButton(onClick = onLocate, modifier = Modifier.testTag("locate")) {
                        Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.my_location))
                    }
                },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(UiR.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            // A wide window (tablet, unfolded foldable): the guide's offer beside the map, so the map stays in view.
            val sidePane = maxWidth >= Sizes.sidePaneMinWindowWidth
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Filters(state, onDiscipline, onSpeciesFilter, onDates, onAnyDates)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        map(MapContent(state.guides, state.userLocation, onVisibleArea, state.featured, onSelect), Modifier.fillMaxSize())
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            else -> GuideCards(state.inView, state.featured, state.guides.isEmpty(), onSelect, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
                if (sidePane) {
                    state.selected?.let { selected ->
                        GuidePane(selected, onUnlock, onTrip, onDismiss, Modifier.width(Sizes.sidePaneWidth).fillMaxHeight())
                    }
                }
            }
            if (!sidePane) {
                state.selected?.let { selected ->
                    GuideSheet(selected = selected, onUnlock = onUnlock, onTrip = onTrip, onDismiss = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Filters(
    state: MapUiState,
    onDiscipline: (Discipline?) -> Unit,
    onSpeciesFilter: (Discipline?) -> Unit,
    onDates: (Trip) -> Unit,
    onAnyDates: () -> Unit,
) {
    val filter = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter.discipline == null,
            onClick = { onDiscipline(null) },
            label = { Text(stringResource(R.string.filter_all)) },
            modifier = Modifier.testTag("discipline-all"),
        )
        Discipline.entries.forEach { discipline ->
            FilterChip(
                selected = filter.discipline == discipline,
                onClick = { onDiscipline(discipline) },
                label = { Text(stringResource(discipline.label)) },
                modifier = Modifier.testTag("discipline-${discipline.name}"),
            )
        }
        SpeciesChip(filter, onSpeciesFilter)
        DatesPicker(filter.dates, state.today, onDates, onAnyDates)
    }
}

/** "When can you go?": the guides with a trip that fits those dates (and those with dates on request). */
@Composable
private fun DatesPicker(dates: Trip?, today: LocalDate, onDates: (Trip) -> Unit, onAnyDates: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    FilterChip(
        selected = dates != null,
        onClick = { open = true },
        label = { Text(dates?.let { context.dateRange(it.start, it.end) } ?: stringResource(R.string.filter_dates)) },
        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(Sizes.iconS)) },
        modifier = Modifier.testTag("dates-picker"),
    )
    if (open) {
        DateRangeDialog(
            today = today,
            initial = dates,
            onPick = {
                open = false
                onDates(it)
            },
            onDismiss = { open = false },
            onClear = {
                open = false
                onAnyDates()
            },
        )
    }
}

/** The species chip: the chosen species open on their own screen ([onSpeciesFilter], with the discipline chosen here). */
@Composable
private fun SpeciesChip(filter: GuideFilter, onSpeciesFilter: (Discipline?) -> Unit) {
    FilterChip(
        selected = filter.species.isNotEmpty(),
        onClick = { onSpeciesFilter(filter.discipline) },
        label = {
            Text(
                when (filter.species.size) {
                    0 -> stringResource(R.string.filter_species)
                    1 -> stringResource(filter.species.single().label)
                    else -> pluralStringResource(R.plurals.filter_species_count, filter.species.size, filter.species.size)
                },
            )
        },
        modifier = Modifier.testTag("species-picker"),
    )
}

/**
 * The guides on screen as cards over the bottom of the map, the [featured] ones first (labelled so), then nearest
 * first: a way in without hunting for pins. [noMatch]: no guide matches the filters anywhere, rather than none in this
 * part of the map.
 */
@Composable
private fun GuideCards(
    guides: List<GuideItem>,
    featured: Set<String>,
    noMatch: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
        val count = pluralStringResource(R.plurals.guide_count, guides.size, guides.size)
        // A pill, so the count reads over any part of the map.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(Sizes.border, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.s),
        ) {
            Text(
                when {
                    noMatch -> stringResource(R.string.no_guides)
                    guides.isEmpty() -> stringResource(R.string.no_guides_here)
                    else -> count
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs).testTag("guide-count"),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(guides, key = { it.guide.id }, contentType = { "guide" }) { item ->
                Card(
                    onClick = { onSelect(item.guide.id) },
                    modifier = Modifier.width(Sizes.guideCardWidth).testTag("guide-${item.guide.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(Spacing.m)) {
                        ActivityLabel(item.guide, item.guide.id in featured)
                        Text(item.guide.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.subtitle(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Approximate is enough for "about 450 km away", and the only location the app ever asks for. */
private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** Denied for good ("Don't ask again"): the app's settings page is the only way to allow the location. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
