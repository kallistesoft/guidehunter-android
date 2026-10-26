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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideFilter
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.ui.label

/** What the map draws: the guides to pin, the user's location, and what a tap on a pin does. */
data class MapContent(
    val guides: List<GuideItem>,
    val userLocation: GeoPoint?,
    val onSelect: (String) -> Unit,
)

/**
 * The home screen: a map of the guides, filters above it and the guides' cards below. A pin or a card opens the
 * guide's offer. [map] draws the map (Google Maps); tests pass a stand-in, as Google Maps can't draw on the JVM.
 */
@Composable
fun MapScreen(
    onBecomeGuide: () -> Unit,
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
        onBecomeGuide = onBecomeGuide,
        onLocate = {
            if (hasLocationPermission(context)) viewModel.refreshLocation() else permission.launch(LOCATION_PERMISSION)
        },
        onPrivacyOptions = viewModel::showPrivacyOptions,
        onDiscipline = viewModel::chooseDiscipline,
        onSpecies = viewModel::chooseSpecies,
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
    onBecomeGuide: () -> Unit,
    onLocate: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onDiscipline: (Discipline?) -> Unit,
    onSpecies: (Species?) -> Unit,
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
                    if (state.privacyOptionsRequired) {
                        IconButton(onClick = onPrivacyOptions, modifier = Modifier.testTag("privacy-options")) {
                            Icon(Icons.Default.Shield, contentDescription = stringResource(R.string.privacy_options))
                        }
                    }
                    TextButton(onClick = onBecomeGuide, modifier = Modifier.testTag("become-guide")) {
                        Text(stringResource(R.string.become_guide))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Filters(state.filter, onDiscipline, onSpecies)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                map(MapContent(state.guides, state.userLocation, onSelect), Modifier.fillMaxSize())
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> GuideCards(state.guides, onSelect, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
    state.selected?.let { selected ->
        GuideSheet(selected = selected, onUnlock = onUnlock, onDismiss = onDismiss)
    }
}

@Composable
private fun Filters(filter: GuideFilter, onDiscipline: (Discipline?) -> Unit, onSpecies: (Species?) -> Unit) {
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
        SpeciesPicker(filter, onSpecies)
    }
}

@Composable
private fun SpeciesPicker(filter: GuideFilter, onSpecies: (Species?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = filter.species != null,
            onClick = { expanded = true },
            label = { Text(filter.species?.let { stringResource(it.label) } ?: stringResource(R.string.filter_species)) },
            modifier = Modifier.testTag("species-picker"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_species_any)) },
                onClick = {
                    expanded = false
                    onSpecies(null)
                },
                modifier = Modifier.testTag("species-any"),
            )
            filter.speciesChoices.forEach { species ->
                DropdownMenuItem(
                    text = { Text(stringResource(species.label)) },
                    onClick = {
                        expanded = false
                        onSpecies(species)
                    },
                    modifier = Modifier.testTag("species-${species.name}"),
                )
            }
        }
    }
}

/** The guides as cards over the bottom of the map, nearest first: a way in without hunting for pins. */
@Composable
private fun GuideCards(guides: List<GuideItem>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
        val count = pluralStringResource(R.plurals.guide_count, guides.size, guides.size)
        Text(
            if (guides.isEmpty()) stringResource(R.string.no_guides) else count,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).testTag("guide-count"),
        )
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
                        Text(
                            stringResource(item.guide.activity.label).uppercase(),
                            style = GhTextStyles.overline,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
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
