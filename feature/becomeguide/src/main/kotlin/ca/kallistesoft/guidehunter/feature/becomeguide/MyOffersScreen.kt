package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.ui.R as UiR

/** The guide's home: their profile and offers. [onSettings]: the settings (mode, account). */
@Composable
fun MyOffersScreen(
    onSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onAddOffer: () -> Unit,
    onEditOffer: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyOffersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyOffersContent(
        state = state,
        actions = MyOffersActions(settings = onSettings, editProfile = onEditProfile, addOffer = onAddOffer, editOffer = onEditOffer),
        modifier = modifier,
    )
}

internal class MyOffersActions(
    val settings: () -> Unit,
    val editProfile: () -> Unit,
    val addOffer: () -> Unit,
    val editOffer: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyOffersContent(state: MyOffersUiState, actions: MyOffersActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_offers_title)) },
                actions = {
                    IconButton(onClick = actions.settings, modifier = Modifier.testTag("settings")) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(UiR.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(Spacing.xxxl)) else Account(state, actions)
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) { content() }
}

@Composable
private fun Account(state: MyOffersUiState, actions: MyOffersActions) = Page {
    ProfileCard(state.profile, actions.editProfile)

    Label(R.string.my_offers_heading)
    if (state.offers.isEmpty()) {
        Text(stringResource(R.string.no_offers), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("no-offers"))
    }
    state.offers.forEach { OfferCard(it, it.draft.schedule.expired(state.today), actions.editOffer) }
    PrimaryButton(
        stringResource(R.string.add_offer),
        actions.addOffer,
        Modifier.fillMaxWidth().testTag("add-offer"),
        icon = Icons.Default.Add,
    )
    Note(stringResource(R.string.review_note))
}

@Composable
private fun ProfileCard(profile: GuideProfile?, onEdit: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("profile")) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (profile == null || !profile.isComplete) {
                Text(stringResource(R.string.profile_missing), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(stringResource(R.string.profile_fill_in), onEdit, Modifier.fillMaxWidth().testTag("edit-profile"))
            } else {
                Text(profile.businessName, style = MaterialTheme.typography.titleMedium)
                listOf(profile.email, profile.phone, profile.website).filter { it.isNotBlank() }.forEach { Note(it) }
                SecondaryButton(stringResource(R.string.profile_edit), onEdit, Modifier.testTag("edit-profile"))
            }
        }
    }
}

@Composable
private fun OfferCard(offer: GuideOffer, expired: Boolean, onEdit: (String) -> Unit) {
    OutlinedCard(onClick = { onEdit(offer.id) }, modifier = Modifier.fillMaxWidth().testTag("offer-${offer.id}")) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(offer.draft.title, style = MaterialTheme.typography.titleMedium)
            Note(offer.draft.region)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val published = offer.status == OfferStatus.PUBLISHED
                Text(
                    stringResource(
                        when {
                            // Its last date is over: off the map whatever its review.
                            expired -> R.string.status_expired
                            published -> R.string.status_published
                            offer.live -> R.string.status_changes_pending
                            else -> R.string.status_pending
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        expired -> MaterialTheme.colorScheme.error
                        published -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.testTag("status-${offer.id}"),
                )
            }
        }
    }
}
