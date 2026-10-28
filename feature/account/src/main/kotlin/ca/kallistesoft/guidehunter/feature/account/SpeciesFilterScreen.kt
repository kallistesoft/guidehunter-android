package ca.kallistesoft.guidehunter.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SectionToggle
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.speciesByDiscipline

/**
 * The species the map shows, on their own screen (from the map's species chip or from Settings): each tap is saved at
 * once, kept for next time; "Show on the map" (or back) returns. [discipline]: the one chosen on the map, whose species
 * alone are listed; null lists all of them.
 */
@Composable
fun SpeciesFilterScreen(
    discipline: Discipline?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeciesFilterViewModel = hiltViewModel<SpeciesFilterViewModel, SpeciesFilterViewModel.Factory>(
        creationCallback = { it.create(discipline) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SpeciesFilterContent(state, onBack, viewModel::toggle, viewModel::clear, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeciesFilterContent(
    state: SpeciesFilterUiState,
    onBack: () -> Unit,
    onToggle: (Species) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.species_filter_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onClear, enabled = state.chosen.isNotEmpty(), modifier = Modifier.testTag("species-clear")) {
                        Text(stringResource(UiR.string.dialog_clear))
                    }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.m), Alignment.Center) {
                PrimaryButton(
                    stringResource(R.string.species_filter_done),
                    onBack,
                    Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().testTag("species-done"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(Spacing.xxxl))
                return@Box
            }
            Column(
                Modifier
                    .widthIn(max = Sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.s)
                    .testTag("species-filter-screen"),
            ) {
                Text(
                    stringResource(R.string.species_filter_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
                )
                SpeciesRow(stringResource(R.string.species_filter_any), state.chosen.isEmpty(), "species-any", onClear)
                SpeciesGroups(state, onToggle)
            }
        }
    }
}

/**
 * The species under their discipline (hunting, then fishing, alphabetical in the app's language). With both
 * disciplines, each list opens and closes under its heading, all open at first.
 */
@Composable
private fun SpeciesGroups(state: SpeciesFilterUiState, onToggle: (Species) -> Unit) {
    val context = LocalContext.current
    val groups = remember(state.choices, LocalConfiguration.current) { context.speciesByDiscipline(state.choices) }
    var closed by rememberSaveable { mutableStateOf(emptyList<Discipline>()) }
    groups.forEach { (discipline, species) ->
        val shown = discipline !in closed
        if (groups.size > 1) {
            val chosen = species.count { it in state.chosen }
            SectionToggle(
                title = stringResource(discipline.label),
                expanded = shown,
                onToggle = { closed = if (shown) closed + discipline else closed - discipline },
                detail = if (chosen > 0) "$chosen/${species.size}" else species.size.toString(),
                modifier = Modifier.padding(horizontal = Spacing.screen).testTag("species-group-${discipline.name}"),
            )
        }
        if (shown) {
            species.forEach { choice ->
                SpeciesRow(stringResource(choice.label), choice in state.chosen, "species-${choice.name}") { onToggle(choice) }
            }
        }
    }
}

@Composable
private fun SpeciesRow(text: String, checked: Boolean, tag: String, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.s)
            .testTag(tag),
    )
}
