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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/** Where the map's "Become a guide" leads: Google sign-in, then the guide's profile and offers. */
@Composable
fun MyOffersScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onAddOffer: () -> Unit,
    onEditOffer: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyOffersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MyOffersContent(
        state = state,
        actions = MyOffersActions(
            back = onBack,
            signIn = { viewModel.signIn(context) },
            signOut = { viewModel.signOut(context) },
            deleteAccount = { viewModel.deleteAccount(context) },
            dismissError = viewModel::dismissError,
            editProfile = onEditProfile,
            addOffer = onAddOffer,
            editOffer = onEditOffer,
        ),
        modifier = modifier,
    )
}

internal class MyOffersActions(
    val back: () -> Unit,
    val signIn: () -> Unit,
    val signOut: () -> Unit,
    val deleteAccount: () -> Unit,
    val dismissError: () -> Unit,
    val editProfile: () -> Unit,
    val addOffer: () -> Unit,
    val editOffer: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyOffersContent(state: MyOffersUiState, actions: MyOffersActions, modifier: Modifier = Modifier) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.user == null) R.string.become_guide_title else R.string.my_offers_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.back, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
                actions = {
                    if (state.user != null) {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("account-menu")) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.account_menu))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out)) },
                                onClick = {
                                    menu = false
                                    actions.signOut()
                                },
                                modifier = Modifier.testTag("sign-out"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_account)) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                                modifier = Modifier.testTag("delete-account"),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(Spacing.xxxl))
                state.user == null -> SignIn(state, actions)
                else -> Account(state, actions)
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
private fun SignIn(state: MyOffersUiState, actions: MyOffersActions) = Page {
    Text(stringResource(R.string.become_guide_intro), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.sign_in_why), style = MaterialTheme.typography.bodyMedium)
    PrimaryButton(
        stringResource(if (state.busy) R.string.signing_in else R.string.sign_in_google),
        actions.signIn,
        Modifier.fillMaxWidth().testTag("sign-in"),
        enabled = !state.busy,
    )
    AccountErrorText(state.error, actions.dismissError)
}

@Composable
private fun Account(state: MyOffersUiState, actions: MyOffersActions) = Page {
    Note(stringResource(R.string.signed_in_as, state.user?.email.orEmpty()), Modifier.testTag("signed-in-as"))
    AccountErrorText(state.error, actions.dismissError)
    ProfileCard(state.profile, actions.editProfile)

    Label(R.string.my_offers_heading)
    if (state.offers.isEmpty()) {
        Text(stringResource(R.string.no_offers), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("no-offers"))
    }
    state.offers.forEach { OfferCard(it, actions.editOffer) }
    PrimaryButton(
        stringResource(R.string.add_offer),
        actions.addOffer,
        Modifier.fillMaxWidth().testTag("add-offer"),
        enabled = !state.busy,
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
private fun OfferCard(offer: GuideOffer, onEdit: (String) -> Unit) {
    OutlinedCard(onClick = { onEdit(offer.id) }, modifier = Modifier.fillMaxWidth().testTag("offer-${offer.id}")) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(offer.draft.title, style = MaterialTheme.typography.titleMedium)
            Note(offer.draft.region)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val published = offer.status == OfferStatus.PUBLISHED
                Text(
                    stringResource(
                        when {
                            published -> R.string.status_published
                            offer.live -> R.string.status_changes_pending
                            else -> R.string.status_pending
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (published) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("status-${offer.id}"),
                )
            }
        }
    }
}

@Composable
private fun AccountErrorText(error: AccountError?, onDismiss: () -> Unit) {
    if (error == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Error(
                stringResource(
                    when (error) {
                        AccountError.SIGN_IN_FAILED -> R.string.sign_in_failed
                        AccountError.NOT_CONFIGURED -> R.string.sign_in_not_configured
                        AccountError.DELETE_FAILED -> R.string.delete_account_failed
                    },
                ),
            )
        }
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-error")) { Text(stringResource(R.string.dismiss)) }
    }
}
