package ca.kallistesoft.guidehunter.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.AppMode

/** After sign-in: "I'm looking for a guide" or "I'm a guide". The app then opens on that mode's home. */
@Composable
fun ChooseModeScreen(modifier: Modifier = Modifier, viewModel: ChooseModeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChooseModeContent(state, viewModel::choose, modifier)
}

@Composable
internal fun ChooseModeContent(state: ChooseModeUiState, onChoose: (AppMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xxxl)
                .widthIn(max = Sizes.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (state.email.isNotEmpty()) {
                Text(
                    stringResource(R.string.signed_in_as, state.email),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("signed-in-as"),
                )
            }
            Text(stringResource(R.string.choose_mode_title), style = GhTextStyles.brand)
            Text(stringResource(R.string.choose_mode_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModeCard(Icons.Default.Search, R.string.mode_user_title, R.string.mode_user_body, "mode-user") { onChoose(AppMode.USER) }
            ModeCard(Icons.Default.Place, R.string.mode_guide_title, R.string.mode_guide_body, "mode-guide") { onChoose(AppMode.GUIDE) }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: Int, body: Int, tag: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(Sizes.cardPadding), horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.touchTarget))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
