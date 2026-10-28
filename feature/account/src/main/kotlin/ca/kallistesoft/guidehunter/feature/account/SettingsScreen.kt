package ca.kallistesoft.guidehunter.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.AppMode
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.speciesByDiscipline

/** Settings, for both modes. Switching mode sends the app to the other home (the app watches the mode). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onGuideProfile: () -> Unit,
    onSpeciesFilter: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsContent(
        state = state,
        actions = SettingsActions(
            back = onBack,
            guideProfile = onGuideProfile,
            speciesFilter = onSpeciesFilter,
            switchMode = { viewModel.switchMode(context) },
            signIn = { viewModel.signIn(context) },
            signOut = { viewModel.signOut(context) },
            deleteAccount = { viewModel.deleteAccount(context) },
            privacyOptions = viewModel::showPrivacyOptions,
            dismissError = viewModel::dismissError,
        ),
        modifier = modifier,
    )
}

internal class SettingsActions(
    val back: () -> Unit,
    val guideProfile: () -> Unit,
    val speciesFilter: () -> Unit,
    val switchMode: () -> Unit,
    val signIn: () -> Unit,
    val signOut: () -> Unit,
    val deleteAccount: () -> Unit,
    val privacyOptions: () -> Unit,
    val dismissError: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = actions.back, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
            )
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
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .testTag("settings-screen"),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                AccountErrorText(state.error, actions.dismissError, Modifier.padding(horizontal = Spacing.xs))
                val guide = state.mode == AppMode.GUIDE
                if (guide) {
                    Section(R.string.section_guide) {
                        SettingsRow(Icons.Default.Storefront, R.string.guide_profile, stringResource(R.string.guide_profile_body), "guide-profile", actions.guideProfile)
                    }
                } else {
                    Section(R.string.section_looking) {
                        SettingsRow(Icons.Default.FilterList, R.string.species_on_map, speciesSummary(state), "species-filter", actions.speciesFilter)
                    }
                }
                Section(R.string.section_mode) {
                    SettingsRow(
                        Icons.Default.SwapHoriz,
                        if (guide) R.string.switch_to_user else R.string.switch_to_guide,
                        stringResource(if (guide) R.string.switch_to_user_body else R.string.switch_to_guide_body),
                        "switch-mode",
                        actions.switchMode,
                        enabled = !state.busy,
                        accent = true,
                    )
                }
                Section(R.string.section_account) {
                    val user = state.user
                    if (user == null) {
                        SettingsRow(Icons.Default.AccountCircle, R.string.sign_in_google, stringResource(R.string.sign_in_body), "sign-in", actions.signIn, enabled = !state.busy)
                    } else {
                        ListItem(
                            headlineContent = { Text(user.email) },
                            supportingContent = { Text(stringResource(R.string.signed_in_with_google)) },
                            leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            modifier = Modifier.testTag("account"),
                        )
                    }
                    if (state.privacyOptionsRequired) {
                        HorizontalDivider()
                        SettingsRow(Icons.Default.Shield, R.string.privacy_options, stringResource(R.string.privacy_options_body), "privacy-options", actions.privacyOptions)
                    }
                    if (user != null) {
                        HorizontalDivider()
                        SettingsRow(Icons.AutoMirrored.Filled.Logout, R.string.sign_out, null, "sign-out", actions.signOut, enabled = !state.busy)
                        HorizontalDivider()
                        SettingsRow(Icons.Default.Delete, R.string.delete_account, null, "delete-account", { confirmDelete = true }, enabled = !state.busy, danger = true)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.deleteAccount()
                    },
                    modifier = Modifier.testTag("delete-account-confirm"),
                ) { Text(stringResource(R.string.delete_account)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete-account-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** The chosen species' names, in the pickers' order, or "Any species". */
@Composable
private fun speciesSummary(state: SettingsUiState): String {
    val context = LocalContext.current
    val any = stringResource(R.string.species_filter_any)
    return remember(state.speciesFilter, LocalConfiguration.current) {
        context.speciesByDiscipline(state.speciesFilter).flatMap { it.second }
            .joinToString(", ") { context.getString(it.label) }
            .ifEmpty { any }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.m),
    )
    OutlinedCard(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: Int,
    supporting: String?,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
    danger: Boolean = false,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified
    ListItem(
        headlineContent = { Text(stringResource(label), color = color) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    danger -> MaterialTheme.colorScheme.error
                    accent -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).testTag(tag),
    )
}
