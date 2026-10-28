package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.ads.NativeAdCard
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.designsystem.component.FeaturedBadge
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhColors
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.Boost
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.date
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The guide's home: their profile and offers. [onRequests]: the requests received; [onSettings]: mode, account. */
@Composable
fun MyOffersScreen(
    onRequests: () -> Unit,
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
        actions = MyOffersActions(
            requests = onRequests,
            settings = onSettings,
            editProfile = onEditProfile,
            addOffer = onAddOffer,
            editOffer = onEditOffer,
            prepareBoost = viewModel::prepareBoost,
            boost = viewModel::boost,
        ),
        nativeAds = viewModel.nativeAds,
        modifier = modifier,
    )
}

internal class MyOffersActions(
    val requests: () -> Unit,
    val settings: () -> Unit,
    val editProfile: () -> Unit,
    val addOffer: () -> Unit,
    val editOffer: (String) -> Unit,
    /** The boost's explanation opened: its ad starts loading. */
    val prepareBoost: () -> Unit = {},
    /** "Watch an ad to boost" an offer. */
    val boost: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyOffersContent(
    state: MyOffersUiState,
    actions: MyOffersActions,
    nativeAds: NativeAds,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_offers_title)) },
                actions = {
                    val pending = state.pendingRequests
                    IconButton(onClick = actions.requests, modifier = Modifier.testTag("requests-received")) {
                        BadgedBox(
                            badge = {
                                if (pending > 0) Badge(Modifier.testTag("requests-badge")) { Text(pending.toString()) }
                            },
                        ) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = if (pending > 0) {
                                    pluralStringResource(R.plurals.requests_badge, pending, pending)
                                } else {
                                    stringResource(UiR.string.requests_received)
                                },
                            )
                        }
                    }
                    IconButton(onClick = actions.settings, modifier = Modifier.testTag("settings")) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(UiR.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(Spacing.xxxl)) else Account(state, actions, nativeAds)
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
private fun Account(state: MyOffersUiState, actions: MyOffersActions, nativeAds: NativeAds) = Page {
    ProfileCard(state.profile, actions.editProfile)

    Label(R.string.my_offers_heading)
    if (state.offers.isEmpty()) {
        Text(stringResource(R.string.no_offers), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("no-offers"))
    }
    // The offer whose boost is being explained, kept over a rotation.
    var explaining by rememberSaveable { mutableStateOf<String?>(null) }
    state.offers.forEach { offer ->
        val expired = offer.draft.schedule.expired(state.today)
        OfferCard(offer, expired, offer.isBoosted(state.now), actions.editOffer) {
            // Only an offer on the map can be featured on it.
            if (offer.live && !expired) {
                BoostRow(offer, state.now, state.boost?.takeIf { it.offerId == offer.id }) {
                    explaining = offer.id
                    actions.prepareBoost()
                }
            }
        }
    }
    state.offers.find { it.id == explaining }?.let { offer ->
        BoostSheet(
            title = offer.draft.title,
            onBoost = {
                explaining = null
                actions.boost(offer.id)
            },
            onDismiss = { explaining = null },
        )
    }
    PrimaryButton(
        stringResource(R.string.add_offer),
        actions.addOffer,
        Modifier.fillMaxWidth().testTag("add-offer"),
        icon = Icons.Default.Add,
    )
    // One ad below the offers, none while there are none; it takes no room until one loads.
    if (state.offers.isNotEmpty()) NativeAdCard(nativeAds)
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
private fun OfferCard(offer: GuideOffer, expired: Boolean, featured: Boolean, onEdit: (String) -> Unit, boost: @Composable () -> Unit) {
    // A featured offer stands out in gold, as on the map.
    OutlinedCard(
        onClick = { onEdit(offer.id) },
        modifier = Modifier.fillMaxWidth().testTag("offer-${offer.id}"),
        border = if (featured) BorderStroke(Sizes.borderFeatured, GhColors.featured.ring) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (featured) FeaturedBadge(stringResource(UiR.string.featured), Modifier.testTag("featured-${offer.id}"))
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
            boost()
        }
    }
}

/**
 * An offer on the map: whether it's featured ("Featured until Oct 31", "Featured ended Oct 25", or the pitch), and
 * Boost, offered again only when less than a day is left or once the boost has ended. [attempt]: this offer's boost
 * under way or failed.
 */
@Composable
private fun BoostRow(offer: GuideOffer, now: Instant, attempt: BoostAttempt?, onBoost: () -> Unit) {
    val context = LocalContext.current
    val until = offer.boostedUntil
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val note = when {
            until == null -> stringResource(R.string.boost_pitch)
            until > now -> stringResource(R.string.boost_until, context.date(until.localDate()))
            else -> stringResource(R.string.boost_ended, context.date(until.localDate()))
        }
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag("boost-note-${offer.id}"),
        )
        when {
            attempt != null && attempt.error == null -> CircularProgressIndicator(Modifier.size(Sizes.iconL).testTag("boosting-${offer.id}"))
            Boost.canBoost(until, now) -> BoostButton(
                stringResource(if (until == null) R.string.boost else R.string.boost_again),
                onBoost,
                Modifier.testTag("boost-${offer.id}"),
            )
        }
    }
    attempt?.error?.let { error ->
        Text(
            stringResource(
                when (error) {
                    BoostError.NOT_EARNED -> R.string.boost_error_not_earned
                    BoostError.OFFLINE -> R.string.boost_error_offline
                    BoostError.FAILED -> R.string.boost_error_failed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("boost-error-${offer.id}"),
        )
    }
}

/** The mockup's gold "Boost" button (design/mockup/GuideHome.dc.html). */
@Composable
private fun BoostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GhColors.featured
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        colors = ButtonDefaults.buttonColors(containerColor = colors.tint, contentColor = colors.text),
        contentPadding = PaddingValues(horizontal = Spacing.m),
    ) {
        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(Sizes.iconS))
        Text(text, modifier = Modifier.padding(start = Spacing.xs))
    }
}

/**
 * What a boost does, before the ad (design/mockup/BoostSheet.dc.html): 3 days on top of the map, at most 3 featured
 * at a time, an ad to start it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoostSheet(title: String, onBoost: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl).testTag("boost-sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = GhColors.featured.tint, contentColor = GhColors.featured.ring) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.padding(Spacing.m).size(Sizes.iconXl))
            }
            Text(stringResource(R.string.boost_title, title), style = MaterialTheme.typography.headlineSmall)
            listOf(R.string.boost_point_pin, R.string.boost_point_turns, R.string.boost_point_ad).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
            PrimaryButton(
                stringResource(R.string.boost_watch_ad),
                onBoost,
                Modifier.fillMaxWidth().testTag("boost-watch-ad"),
                icon = Icons.Default.PlayCircle,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("boost-not-now")) {
                Text(stringResource(R.string.boost_not_now))
            }
        }
    }
}

/** A day in the guide's time zone, for "Featured until …". */
private fun Instant.localDate(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date
