package ca.kallistesoft.guidehunter.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing

/** The first launch: sign in with Google ([onSignedIn]: then user or guide), or "Later". */
@Composable
fun WelcomeScreen(onSignedIn: () -> Unit, modifier: Modifier = Modifier, viewModel: WelcomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    WelcomeContent(
        state = state,
        onSignIn = { viewModel.signIn(context, onSignedIn) },
        onLater = viewModel::later,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

@Composable
internal fun WelcomeContent(
    state: WelcomeUiState,
    onSignIn: () -> Unit,
    onLater: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl)
                .widthIn(max = Sizes.contentMaxWidth)
                .testTag("welcome"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Spacer(Modifier.height(Spacing.xxxl))
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.iconHero))
            Text(stringResource(R.string.welcome_title), style = GhTextStyles.brand, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.l))
            PrimaryButton(
                stringResource(if (state.busy) R.string.signing_in else R.string.sign_in_google),
                onSignIn,
                Modifier.fillMaxWidth().testTag("sign-in"),
                enabled = !state.busy,
                icon = Icons.Default.AccountCircle,
            )
            AccountErrorText(state.error, onDismissError, Modifier.fillMaxWidth())
            TextButton(onClick = onLater, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("later")) {
                Text(stringResource(R.string.later))
            }
            Text(
                stringResource(R.string.later_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
