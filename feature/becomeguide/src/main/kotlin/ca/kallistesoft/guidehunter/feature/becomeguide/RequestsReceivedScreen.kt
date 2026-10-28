package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import ca.kallistesoft.guidehunter.core.ads.NativeAdCard
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.ui.ContactLink
import ca.kallistesoft.guidehunter.core.ui.LinkOpener
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.StatusChip
import ca.kallistesoft.guidehunter.core.ui.day
import ca.kallistesoft.guidehunter.core.ui.rememberLinkOpener
import ca.kallistesoft.guidehunter.core.ui.requestTrip

/** "Requests received": the pending ones to answer (call, email, accept, decline), then the answered ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsReceivedScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestsReceivedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The request whose decline is being confirmed: its id and the user's name.
    var declining by rememberSaveable { mutableStateOf<List<String>?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.requests_received)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(Spacing.xxxl))
                state.pending.isEmpty() && state.answered.isEmpty() -> Text(
                    stringResource(R.string.no_requests_received),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).padding(Spacing.screen).testTag("no-requests"),
                )
                else -> Received(
                    state,
                    rememberLinkOpener(),
                    onAccept = viewModel::accept,
                    onDecline = { declining = arrayListOf(it.id, it.user.name) },
                    onEditProfile = onEditProfile,
                    nativeAds = viewModel.nativeAds,
                )
            }
        }
    }
    declining?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { declining = null },
            title = { Text(stringResource(R.string.decline_title, name)) },
            text = { Text(stringResource(R.string.decline_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        declining = null
                        viewModel.decline(id)
                    },
                    modifier = Modifier.testTag("decline-confirm"),
                ) { Text(stringResource(R.string.decline), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { declining = null }, modifier = Modifier.testTag("decline-cancel")) {
                    Text(stringResource(UiR.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun Received(
    state: RequestsReceivedUiState,
    links: LinkOpener,
    onAccept: (String) -> Unit,
    onDecline: (InterestRequest) -> Unit,
    onEditProfile: () -> Unit,
    nativeAds: NativeAds,
) {
    LazyColumn(
        Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().testTag("requests-received-list"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        state.error?.let { error ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Error(
                        stringResource(
                            when (error) {
                                ReceivedError.FAILED -> R.string.requests_change_failed
                                ReceivedError.NEEDS_PROFILE -> R.string.requests_needs_profile
                            },
                        ),
                    )
                    if (error == ReceivedError.NEEDS_PROFILE) {
                        PrimaryButton(stringResource(R.string.profile_fill_in), onEditProfile, Modifier.fillMaxWidth().testTag("edit-profile"))
                    }
                }
            }
        }
        if (state.pending.isNotEmpty()) {
            item { Header(stringResource(R.string.requests_waiting_header, state.pending.size)) }
            items(state.pending, key = { it.id }) { request ->
                PendingCard(request, state.busy, links, onAccept = { onAccept(request.id) }, onDecline = { onDecline(request) })
            }
            item(key = "native-ad") { NativeAdCard(nativeAds) }
        }
        if (state.answered.isNotEmpty()) {
            item { Header(stringResource(R.string.requests_answered)) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    state.answered.forEachIndexed { index, request ->
                        if (index > 0) HorizontalDivider()
                        AnsweredRow(request)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A request to answer: the offer and trip, who and how to reach them, when, Accept and Decline. */
@Composable
private fun PendingCard(request: InterestRequest, busy: Boolean, links: LinkOpener, onAccept: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth().testTag("request-${request.id}")) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Note("${request.offerTitle} · ${context.requestTrip(request.trip)}")
            Text(request.user.name, style = MaterialTheme.typography.titleLarge)
            if (request.user.phone.isNotBlank()) {
                ContactLink(Icons.Default.Phone, stringResource(UiR.string.contact_call, request.user.phone), "call-${request.id}") {
                    links.dial(request.user.phone)
                }
            }
            ContactLink(Icons.Default.Email, stringResource(UiR.string.contact_email, request.user.email), "email-${request.id}") {
                links.email(request.user.email, request.offerTitle)
            }
            Note(stringResource(R.string.request_received_on, context.day(request.createdAt)))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
                PrimaryButton(stringResource(R.string.accept), onAccept, Modifier.weight(1f).testTag("accept-${request.id}"), enabled = !busy)
                SecondaryButton(stringResource(R.string.decline), onDecline, Modifier.weight(1f).testTag("decline-${request.id}"), enabled = !busy)
            }
        }
    }
}

@Composable
private fun AnsweredRow(request: InterestRequest) {
    Row(
        Modifier.fillMaxWidth().padding(Spacing.l).testTag("answered-${request.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(request.user.name, style = MaterialTheme.typography.titleSmall)
            Note(request.offerTitle)
        }
        StatusChip(request.status)
    }
}
