package ca.kallistesoft.guidehunter.feature.becomeguide

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
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
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.OfferField
import ca.kallistesoft.guidehunter.core.model.OfferStep
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.ui.DateDialog
import ca.kallistesoft.guidehunter.core.ui.DateRangeDialog
import ca.kallistesoft.guidehunter.core.ui.R as UiR
import ca.kallistesoft.guidehunter.core.ui.departureDate
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.speciesByDiscipline
import ca.kallistesoft.guidehunter.core.ui.tripLength
import kotlinx.datetime.LocalDate

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
            length = viewModel::setLength,
            days = viewModel::setDays,
            addDepartures = viewModel::addDepartures,
            addWeekly = viewModel::addWeekly,
            removeDeparture = viewModel::removeDeparture,
            everyYear = viewModel::setEveryYear,
            price = viewModel::setPrice,
            next = viewModel::next,
            back = { if (!viewModel.back()) onBack() },
            change = viewModel::change,
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
    val length: (TripLength) -> Unit,
    val days: (String) -> Unit,
    val addDepartures: (List<LocalDate>, Set<Slot>) -> Unit,
    val addWeekly: (LocalDate, LocalDate, Set<Slot>) -> Unit,
    val removeDeparture: (Departure) -> Unit,
    val everyYear: (Boolean) -> Unit,
    val price: (String) -> Unit,
    /** Next step, or send on the summary. */
    val next: () -> Unit,
    /** Previous step, or leaves the form. */
    val back: () -> Unit,
    /** Opens a step from the summary. */
    val change: (OfferStep) -> Unit,
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
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // System back goes a step back, like the top bar's arrow.
    BackHandler(enabled = !state.loading) { actions.back() }
    val step = state.step
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(step.title), modifier = Modifier.testTag("step-title"))
                            Text(
                                stringResource(R.string.step_of, step.ordinal + 1, OfferStep.entries.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = actions.back, modifier = Modifier.testTag("back")) {
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
                LinearProgressIndicator(
                    progress = { (step.ordinal + 1f) / OfferStep.entries.size },
                    modifier = Modifier.fillMaxWidth().testTag("step-progress"),
                )
            }
        },
        bottomBar = { if (!state.loading) StepButtons(state, actions) },
    ) { padding ->
        // The insets are consumed, so the form's imePadding adds the keyboard's height only, not the bars' again.
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (!state.loading) {
                StepColumn {
                    when (step) {
                        OfferStep.WHAT -> WhatStep(state, actions)
                        OfferStep.SPECIES -> SpeciesStep(state, actions)
                        OfferStep.WHERE -> WhereStep(state, actions, locationDenied, picker)
                        OfferStep.WHEN -> WhenStep(state, actions)
                        OfferStep.REVIEW -> ReviewStep(state, actions)
                    }
                }
            }
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

private val OfferStep.title: Int
    get() = when (this) {
        OfferStep.WHAT -> R.string.step_what
        OfferStep.SPECIES -> R.string.step_species
        OfferStep.WHERE -> R.string.step_where
        OfferStep.WHEN -> R.string.step_when
        OfferStep.REVIEW -> R.string.step_review
    }

/** Back and Next under every step; "Send for review" on the summary. */
@Composable
private fun StepButtons(state: OfferEditorUiState, actions: FormActions) {
    val review = state.step == OfferStep.REVIEW
    val sending = state.send == SendState.SENDING
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // An edit opens on the summary: nothing before it to go back to.
        if (state.step != OfferStep.WHAT && !(review && state.editing)) {
            SecondaryButton(stringResource(R.string.step_back), actions.back, Modifier.weight(1f).testTag("step-back"))
        }
        PrimaryButton(
            stringResource(
                when {
                    !review -> R.string.step_next
                    sending -> R.string.sending
                    else -> R.string.send_for_review
                },
            ),
            actions.next,
            Modifier.weight(1f).testTag(if (review) "send" else "next"),
            enabled = !sending,
        )
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) { content() }
}

/** Step 1: what the trip is. */
@Composable
private fun WhatStep(state: OfferEditorUiState, actions: FormActions) {
    val form = state.form
    val errors = state.errors
    Text(
        stringResource(if (state.live) R.string.offer_live_intro else R.string.offer_intro),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag("offer-intro"),
    )
    Field(form.title, actions.title, R.string.field_title, "field-title", errors.message(OfferField.TITLE), help = stringResource(R.string.field_title_help))
    Label(R.string.field_activity)
    // One type per offer: a guide with both makes a hunting offer and a fishing offer.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        GuideActivity.forOffers.forEachIndexed { index, activity ->
            SegmentedButton(
                selected = form.activity == activity,
                onClick = { actions.activity(activity) },
                shape = SegmentedButtonDefaults.itemShape(index, GuideActivity.forOffers.size),
                modifier = Modifier.testTag("activity-${activity.name}"),
            ) { Text(stringResource(activity.label)) }
        }
    }
    Note(stringResource(R.string.field_activity_help))
    errors.message(OfferField.ACTIVITY)?.let { Error(it) }
    Field(
        form.description,
        actions.description,
        R.string.field_description,
        "field-description",
        errors.message(OfferField.DESCRIPTION),
        help = stringResource(R.string.field_description_help, OfferDraft.MIN_DESCRIPTION),
        singleLine = false,
    )
}

/** Step 2: the species of the offer's type, in alphabetical order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesStep(state: OfferEditorUiState, actions: FormActions) {
    val form = state.form
    val activity = form.activity
    if (activity !in GuideActivity.forOffers) {
        // An older "both" offer: its type is chosen on the first step first.
        Text(stringResource(R.string.field_species_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val context = LocalContext.current
        val species = remember(activity, LocalConfiguration.current) {
            context.speciesByDiscipline(Species.forActivity(activity!!)).flatMap { it.second }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            species.forEach { choice ->
                FilterChip(
                    selected = choice in form.species,
                    onClick = { actions.species(choice) },
                    label = { Text(stringResource(choice.label)) },
                    modifier = Modifier.testTag("species-${choice.name}"),
                )
            }
        }
    }
    state.errors.message(OfferField.SPECIES)?.let { Error(it) }
}

/** Step 3: where hunters and anglers meet the guide. */
@Composable
private fun WhereStep(
    state: OfferEditorUiState,
    actions: FormActions,
    locationDenied: Boolean,
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
) {
    val form = state.form
    var coordinates by rememberSaveable { mutableStateOf(false) }
    Field(form.region, actions.region, R.string.field_region, "field-region", state.errors.message(OfferField.REGION), last = true)
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
    state.errors.message(OfferField.LOCATION)?.let { Error(it) }
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
}

/** The lengths offered as chips; any other number of days is typed. */
private val LENGTHS = listOf(TripLength.HalfDay, TripLength.Days(1), TripLength.Days(2), TripLength.Days(3), TripLength.Days(7))

/** Step 4: how long a trip lasts and when they leave. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WhenStep(state: OfferEditorUiState, actions: FormActions) {
    val schedule = state.form.schedule
    val context = LocalContext.current
    val length = schedule.length
    val other = length is TripLength.Days && length !in LENGTHS
    Label(R.string.field_duration)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        LENGTHS.forEach { choice ->
            FilterChip(
                selected = length == choice,
                onClick = { actions.length(choice) },
                label = { Text(context.tripLength(choice)) },
                modifier = Modifier.testTag(if (choice is TripLength.Days) "length-${choice.count}" else "length-half"),
            )
        }
        FilterChip(
            selected = other,
            onClick = { if (!other) actions.days("") },
            label = { Text(stringResource(R.string.duration_other)) },
            modifier = Modifier.testTag("length-other"),
        )
    }
    if (other) {
        val days = (length as TripLength.Days).count
        Field(days.takeIf { it > 0 }?.toString().orEmpty(), actions.days, R.string.field_days, "field-days", null, keyboard = KeyboardType.Number, last = true)
    }
    state.errors.message(OfferField.DURATION)?.let { Error(it) }

    Label(R.string.field_departures)
    Note(stringResource(R.string.departures_help))
    val half = length == TripLength.HalfDay
    // A half day leaves in the morning, the afternoon or both: the dates added next take these.
    var slots by rememberSaveable { mutableStateOf(listOf(Slot.MORNING.name)) }
    val chosenSlots = slots.map(Slot::valueOf).toSet()
    if (half) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.half_day_slots), style = MaterialTheme.typography.bodyMedium)
            Slot.entries.forEach { slot ->
                FilterChip(
                    selected = slot in chosenSlots,
                    // At least one stays chosen.
                    onClick = { if (slot !in chosenSlots || chosenSlots.size > 1) slots = (chosenSlots xor slot).map { it.name } },
                    label = { Text(stringResource(if (slot == Slot.MORNING) R.string.slot_morning else R.string.slot_afternoon)) },
                    modifier = Modifier.testTag("slot-${slot.name}"),
                )
            }
        }
    }
    schedule.departures.forEach { departure ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("departure-${departure.date}${departure.slot?.let { "-$it" }.orEmpty()}")) {
            val text = context.departureDate(departure)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.removeDeparture(departure) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_date, text))
            }
        }
    }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SecondaryButton(stringResource(R.string.add_date), { dialog = "one" }, Modifier.weight(1f).testTag("add-date"))
        SecondaryButton(stringResource(R.string.add_weekly), { dialog = "weekly" }, Modifier.weight(1f).testTag("add-weekly"))
    }
    when (dialog) {
        "one" -> DateDialog(state.today, onPick = { dialog = null; actions.addDepartures(listOf(it), chosenSlots) }, onDismiss = { dialog = null })
        "weekly" -> DateRangeDialog(state.today, onPick = { dialog = null; actions.addWeekly(it.start, it.end, chosenSlots) }, onDismiss = { dialog = null })
    }
    state.errors.message(OfferField.DATES)?.let { Error(it) }

    if (schedule.departures.isNotEmpty()) {
        Label(R.string.field_repeat)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false, true).forEachIndexed { index, everyYear ->
                SegmentedButton(
                    selected = schedule.everyYear == everyYear,
                    onClick = { actions.everyYear(everyYear) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    modifier = Modifier.testTag(if (everyYear) "every-year" else "these-dates"),
                ) { Text(stringResource(if (everyYear) R.string.repeat_every_year else R.string.repeat_once), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Note(stringResource(if (schedule.everyYear) R.string.repeat_every_year_help else R.string.repeat_once_help))
    }
}

private infix fun Set<Slot>.xor(slot: Slot) = if (slot in this) this - slot else this + slot

/** Step 5: the price, everything at a glance with a way back to each step, and Send. */
@Composable
private fun ReviewStep(state: OfferEditorUiState, actions: FormActions) {
    val form = state.form
    val context = LocalContext.current
    if (state.live) Text(stringResource(R.string.offer_live_intro), style = MaterialTheme.typography.bodyLarge)
    Field(
        form.priceFrom,
        actions.price,
        R.string.field_price,
        "field-price",
        state.errors.message(OfferField.PRICE),
        keyboard = KeyboardType.Number,
        suffix = stringResource(R.string.field_price_suffix),
        last = true,
    )
    Summary(OfferStep.WHAT, actions) {
        SummaryLine(form.title)
        form.activity?.let { SummaryLine(stringResource(it.label)) }
        Text(form.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    Summary(OfferStep.SPECIES, actions) {
        SummaryLine(form.species.sortedBy { context.getString(it.label) }.joinToString { context.getString(it.label) })
    }
    Summary(OfferStep.WHERE, actions) {
        SummaryLine(form.region)
        form.location?.let { SummaryLine("%.4f, %.4f".format(java.util.Locale.ROOT, it.latitude, it.longitude)) }
    }
    Summary(OfferStep.WHEN, actions) {
        form.schedule.length?.let { SummaryLine(context.tripLength(it)) }
        val departures = form.schedule.departures
        SummaryLine(
            if (departures.isEmpty()) {
                stringResource(R.string.dates_on_request)
            } else {
                pluralStringResource(R.plurals.departures_count, departures.size, departures.size, context.departureDate(departures.first())) +
                    if (form.schedule.everyYear) " · " + stringResource(R.string.repeat_every_year) else ""
            },
        )
    }
    if (state.needsProfile) {
        Error(stringResource(R.string.offer_needs_profile))
        SecondaryButton(stringResource(R.string.profile_fill_in), actions.editProfile, Modifier.fillMaxWidth().testTag("needs-profile"))
    }
    SendError(state.send, state.errors.isNotEmpty())
    Note(stringResource(R.string.review_note))
}

/** One step on the summary: its title, what was entered, and "Change". */
@Composable
private fun Summary(step: OfferStep, actions: FormActions, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("summary-${step.name}"),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(step.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.change(step) }, modifier = Modifier.testTag("change-${step.name}")) {
                    Text(stringResource(R.string.change))
                }
            }
            content()
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium)
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
        OfferField.DURATION -> stringResource(R.string.error_duration, TripLength.MAX_DAYS)
        OfferField.DATES -> stringResource(R.string.error_dates, OfferDraft.MAX_DEPARTURES)
        OfferField.PRICE -> stringResource(R.string.error_price)
    }
}

/** Approximate is enough to place the pin near the lodge. */
private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
