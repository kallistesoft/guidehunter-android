package ca.kallistesoft.guidehunter.feature.becomeguide

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.component.SecondaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferField
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.R as UiR

/**
 * A guide's offer, new ([offerId] null) or sent before: the form that puts it on the map once we've reviewed it.
 * [onDone] once sent or deleted; [onEditProfile] when sending needs the guide's profile first. [picker] draws the
 * "pick on map" map (Google Maps); tests pass a stand-in, as Google Maps can't draw on the JVM.
 */
@Composable
fun OfferEditorScreen(
    offerId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfferEditorViewModel = hiltViewModel<OfferEditorViewModel, OfferEditorViewModel.Factory>(
        creationCallback = { it.create(offerId) },
    ),
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit = { location, onPick, pickerModifier ->
        LocationPicker(location, onPick, pickerModifier)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Sent for review, or deleted: back to the guide's offers, where its status shows.
    val done = state.send == SendState.SENT || state.deleted
    LaunchedEffect(done) { if (done) onDone() }
    // "Use my location" needs the (approximate) location permission; refused, the pin is placed another way.
    var locationDenied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useMyLocation() else locationDenied = true
    }
    // Made once: a new instance per keystroke would stop every field from skipping recomposition.
    val actions = remember(viewModel) {
        FormActions(
            title = viewModel::setTitle,
            description = viewModel::setDescription,
            activity = viewModel::setActivity,
            species = viewModel::toggleSpecies,
            region = viewModel::setRegion,
            location = viewModel::setLocation,
            price = viewModel::setPrice,
            submit = viewModel::submit,
            delete = viewModel::delete,
            editProfile = onEditProfile,
            useMyLocation = {
                val granted = ContextCompat.checkSelfPermission(context, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.useMyLocation() else permission.launch(LOCATION_PERMISSION)
            },
        )
    }
    OfferEditorContent(
        state = state,
        actions = actions,
        locationDenied = locationDenied,
        onBack = onBack,
        picker = picker,
        modifier = modifier,
    )
}

/** What the form's fields and buttons do. */
internal class FormActions(
    val title: (String) -> Unit,
    val description: (String) -> Unit,
    val activity: (GuideActivity) -> Unit,
    val species: (Species) -> Unit,
    val region: (String) -> Unit,
    val location: (GeoPoint) -> Unit,
    val price: (String) -> Unit,
    val submit: () -> Unit,
    val delete: () -> Unit,
    val editProfile: () -> Unit,
    val useMyLocation: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfferEditorContent(
    state: OfferEditorUiState,
    actions: FormActions,
    locationDenied: Boolean,
    onBack: () -> Unit,
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.editing) R.string.offer_edit_title else R.string.offer_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
                actions = {
                    if (state.editing && !state.loading) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete-offer")) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.offer_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The insets are consumed, so the form's imePadding adds the keyboard's height only, not the bars' again.
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (!state.loading) Form(state, actions, locationDenied, picker)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.offer_delete_title)) },
            text = { Text(stringResource(if (state.live) R.string.offer_delete_live_body else R.string.offer_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.delete()
                    },
                    modifier = Modifier.testTag("delete-confirm"),
                ) { Text(stringResource(R.string.offer_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: OfferEditorUiState,
    actions: FormActions,
    locationDenied: Boolean,
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
) {
    val form = state.form
    val errors = state.errors
    var coordinates by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .fillMaxWidth()
            .imePadding() // before the scroll, so the scrolling area shrinks above the keyboard
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            stringResource(if (state.live) R.string.offer_live_intro else R.string.offer_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("offer-intro"),
        )

        Field(
            form.title,
            actions.title,
            R.string.field_title,
            "field-title",
            errors.message(OfferField.TITLE),
            help = stringResource(R.string.field_title_help),
        )

        Label(R.string.field_activity)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GuideActivity.entries.forEachIndexed { index, activity ->
                SegmentedButton(
                    selected = form.activity == activity,
                    onClick = { actions.activity(activity) },
                    shape = SegmentedButtonDefaults.itemShape(index, GuideActivity.entries.size),
                    modifier = Modifier.testTag("activity-${activity.name}"),
                ) {
                    // "Hunting and fishing" doesn't fit a third of a phone's width: the button says "Both".
                    val label = if (activity == GuideActivity.BOTH) R.string.activity_both_short else activity.label
                    Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        errors.message(OfferField.ACTIVITY)?.let { Error(it) }

        Label(R.string.field_species)
        val activity = form.activity
        if (activity == null) {
            Text(stringResource(R.string.field_species_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Species.forActivity(activity).forEach { species ->
                    FilterChip(
                        selected = species in form.species,
                        onClick = { actions.species(species) },
                        label = { Text(stringResource(species.label)) },
                        modifier = Modifier.testTag("species-${species.name}"),
                    )
                }
            }
        }
        errors.message(OfferField.SPECIES)?.let { Error(it) }

        Field(
            form.description,
            actions.description,
            R.string.field_description,
            "field-description",
            errors.message(OfferField.DESCRIPTION),
            help = stringResource(R.string.field_description_help, OfferDraft.MIN_DESCRIPTION),
            singleLine = false,
        )
        Field(form.region, actions.region, R.string.field_region, "field-region", errors.message(OfferField.REGION))

        Label(R.string.field_location)
        picker(form.location, actions.location, Modifier.fillMaxWidth().height(Sizes.pickerMapHeight).testTag("location-picker"))
        Note(stringResource(if (form.location == null) R.string.field_location_help else R.string.field_location_set))
        // Two ways that need no map (TalkBack, a switch, or just a guide who knows the coordinates).
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SecondaryButton(stringResource(R.string.use_my_location), actions.useMyLocation, Modifier.weight(1f).testTag("use-my-location"))
            SecondaryButton(stringResource(R.string.type_coordinates), { coordinates = true }, Modifier.weight(1f).testTag("type-coordinates"))
        }
        if (state.locationFailed) Error(stringResource(R.string.location_failed))
        if (locationDenied) Error(stringResource(R.string.location_denied))
        errors.message(OfferField.LOCATION)?.let { Error(it) }
        if (coordinates) {
            CoordinatesEntry(
                initial = form.location,
                onPick = {
                    actions.location(it)
                    coordinates = false
                },
                onCancel = { coordinates = false },
            )
        }

        Field(
            form.priceFrom,
            actions.price,
            R.string.field_price,
            "field-price",
            errors.message(OfferField.PRICE),
            keyboard = KeyboardType.Number,
            suffix = stringResource(R.string.field_price_suffix),
            last = true,
        )

        if (state.needsProfile) {
            Error(stringResource(R.string.offer_needs_profile))
            SecondaryButton(stringResource(R.string.profile_fill_in), actions.editProfile, Modifier.fillMaxWidth().testTag("needs-profile"))
        }
        SendError(state.send, errors.isNotEmpty())
        val sending = state.send == SendState.SENDING
        PrimaryButton(
            stringResource(if (sending) R.string.sending else R.string.send_for_review),
            actions.submit,
            Modifier.fillMaxWidth().testTag("send"),
            enabled = !sending,
        )
        Note(stringResource(R.string.review_note))
    }
}

@Composable
private fun Set<OfferField>.message(field: OfferField): String? {
    if (field !in this) return null
    return when (field) {
        OfferField.TITLE -> stringResource(R.string.error_title)
        OfferField.DESCRIPTION -> stringResource(R.string.error_description, OfferDraft.MIN_DESCRIPTION)
        OfferField.ACTIVITY -> stringResource(R.string.error_activity)
        OfferField.SPECIES -> stringResource(R.string.error_species)
        OfferField.REGION -> stringResource(R.string.error_region)
        OfferField.LOCATION -> stringResource(R.string.error_location)
        OfferField.PRICE -> stringResource(R.string.error_price)
    }
}

/** Approximate is enough to place the pin near the lodge. */
private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
