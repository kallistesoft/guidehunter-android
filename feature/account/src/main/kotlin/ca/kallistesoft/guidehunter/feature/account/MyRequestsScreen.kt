package ca.kallistesoft.guidehunter.feature.account

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.ads.NativeAdCard
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.ui.ConfirmRequestSheet
import ca.kallistesoft.guidehunter.core.ui.ContactDetails
import ca.kallistesoft.guidehunter.core.ui.LinkOpener
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.StatusChip
import ca.kallistesoft.guidehunter.core.ui.day
import ca.kallistesoft.guidehunter.core.ui.rememberLinkOpener
import ca.kallistesoft.guidehunter.core.ui.requestTrip

/** "My requests": the requests the user sent, their status, cancel or send again. [onMyContact]: their contact. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(
    onBack: () -> Unit,
    onMyContact: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.askContact) {
        if (state.askContact) {
            viewModel.contactAsked()
            onMyContact()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.my_requests)) },
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
                state.requests.isEmpty() -> Text(
                    stringResource(R.string.no_requests_sent),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).padding(Spacing.screen).testTag("no-requests"),
                )
                else -> Requests(state, viewModel, rememberLinkOpener())
            }
        }
    }
    state.confirm?.let { request ->
        ConfirmRequestSheet(
            offerTitle = request.offerTitle,
            trip = request.trip,
            contact = state.contact ?: return@let,
            sending = state.busy,
            onSend = viewModel::confirmSend,
            onEdit = onMyContact,
            onDismiss = viewModel::dismissConfirm,
        )
    }
}

@Composable
private fun Requests(state: MyRequestsUiState, viewModel: MyRequestsViewModel, links: LinkOpener) {
    LazyColumn(
        Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().testTag("my-requests-list"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (state.failed) {
            item {
                Text(stringResource(R.string.request_change_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("request-error"))
            }
        }
        state.requests.forEachIndexed { index, request ->
            item(key = request.id) {
                RequestCard(request, state.busy, links, onCancel = { viewModel.cancel(request.id) }, onSendAgain = { viewModel.sendAgain(request.id) })
            }
            // One ad card, after the second request (or the last, with fewer).
            if (index == minOf(1, state.requests.lastIndex)) item(key = "native-ad") { NativeAdCard(viewModel.nativeAds) }
        }
    }
}

@Composable
private fun RequestCard(request: InterestRequest, busy: Boolean, links: LinkOpener, onCancel: () -> Unit, onSendAgain: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth().testTag("request-${request.id}")) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(request.offerTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(request.status)
            }
            val day = context.day(request.updatedAt)
            val dated = when (request.status) {
                RequestStatus.APPROVED -> stringResource(R.string.request_accepted_on, day)
                RequestStatus.CANCELLED -> stringResource(R.string.request_cancelled_on, day)
                else -> stringResource(R.string.request_sent_on, context.day(request.createdAt))
            }
            Text(
                "${context.requestTrip(request.trip)} · $dated",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (request.status) {
                RequestStatus.REJECTED -> Note(stringResource(R.string.request_rejected_note))
                RequestStatus.OFFER_REMOVED -> Note(stringResource(R.string.request_removed_note))
                RequestStatus.APPROVED -> request.guideContact?.let { ContactDetails(it, links, request.offerTitle, request.trip) }
                else -> Unit
            }
            when (request.status) {
                RequestStatus.PENDING, RequestStatus.APPROVED -> TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("cancel-${request.id}")) {
                    Text(stringResource(UiR.string.cancel_request), color = MaterialTheme.colorScheme.error)
                }
                RequestStatus.CANCELLED -> TextButton(onClick = onSendAgain, enabled = !busy, modifier = Modifier.testTag("send-again-${request.id}")) {
                    Text(stringResource(UiR.string.send_again), color = MaterialTheme.colorScheme.secondary)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
