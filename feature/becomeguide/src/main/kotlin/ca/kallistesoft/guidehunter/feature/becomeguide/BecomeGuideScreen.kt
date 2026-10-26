package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.ApplicationField
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.GuideApplication
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.ui.label
import ca.kallistesoft.guidehunter.core.ui.R as UiR

/**
 * "Become a guide": the form a guide fills in to be put on the map. [picker] draws the "pick on map" map (Google Maps);
 * tests pass a stand-in, as Google Maps can't draw on the JVM.
 */
@Composable
fun BecomeGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BecomeGuideViewModel = hiltViewModel(),
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit = { location, onPick, pickerModifier ->
        LocationPicker(location, onPick, pickerModifier)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Made once: a new instance per keystroke would stop every field from skipping recomposition.
    val actions = remember(viewModel) {
        FormActions(
            businessName = viewModel::setBusinessName,
            description = viewModel::setDescription,
            activity = viewModel::setActivity,
            species = viewModel::toggleSpecies,
            region = viewModel::setRegion,
            location = viewModel::setLocation,
            price = viewModel::setPrice,
            email = viewModel::setEmail,
            phone = viewModel::setPhone,
            website = viewModel::setWebsite,
            submit = viewModel::submit,
        )
    }
    BecomeGuideContent(
        state = state,
        actions = actions,
        onBack = onBack,
        picker = picker,
        modifier = modifier,
    )
}

/** What the form's fields and button do. */
internal class FormActions(
    val businessName: (String) -> Unit,
    val description: (String) -> Unit,
    val activity: (GuideActivity) -> Unit,
    val species: (Species) -> Unit,
    val region: (String) -> Unit,
    val location: (GeoPoint) -> Unit,
    val price: (String) -> Unit,
    val email: (String) -> Unit,
    val phone: (String) -> Unit,
    val website: (String) -> Unit,
    val submit: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BecomeGuideContent(
    state: BecomeGuideUiState,
    actions: FormActions,
    onBack: () -> Unit,
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.become_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
            )
        },
    ) { padding ->
        // The insets are consumed, so the form's imePadding adds the keyboard's height only, not the bars' again.
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.send == SendState.SENT) {
                Sent(onBack)
            } else {
                Form(state, actions, picker)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: BecomeGuideUiState,
    actions: FormActions,
    picker: @Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
) {
    val form = state.form
    val errors = state.errors
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .fillMaxWidth()
            .imePadding() // before the scroll, so the scrolling area shrinks above the keyboard
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(stringResource(R.string.become_guide_intro), style = MaterialTheme.typography.bodyLarge)

        Field(form.businessName, actions.businessName, R.string.field_business_name, "field-business-name", errors.message(ApplicationField.BUSINESS_NAME))

        Label(R.string.field_activity)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GuideActivity.entries.forEachIndexed { index, activity ->
                SegmentedButton(
                    selected = form.activity == activity,
                    onClick = { actions.activity(activity) },
                    shape = SegmentedButtonDefaults.itemShape(index, GuideActivity.entries.size),
                    modifier = Modifier.testTag("activity-${activity.name}"),
                ) { Text(stringResource(activity.label)) }
            }
        }
        errors.message(ApplicationField.ACTIVITY)?.let { Error(it) }

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
        errors.message(ApplicationField.SPECIES)?.let { Error(it) }

        Field(
            form.description,
            actions.description,
            R.string.field_description,
            "field-description",
            errors.message(ApplicationField.DESCRIPTION),
            help = stringResource(R.string.field_description_help, GuideApplication.MIN_DESCRIPTION),
            singleLine = false,
        )
        Field(form.region, actions.region, R.string.field_region, "field-region", errors.message(ApplicationField.REGION))

        Label(R.string.field_location)
        picker(form.location, actions.location, Modifier.fillMaxWidth().height(Sizes.pickerMapHeight).testTag("location-picker"))
        Text(
            stringResource(if (form.location == null) R.string.field_location_help else R.string.field_location_set),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errors.message(ApplicationField.LOCATION)?.let { Error(it) }

        Field(
            form.priceFrom,
            actions.price,
            R.string.field_price,
            "field-price",
            errors.message(ApplicationField.PRICE),
            keyboard = KeyboardType.Number,
            suffix = stringResource(R.string.field_price_suffix),
        )
        Text(stringResource(R.string.contact_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(form.email, actions.email, R.string.field_email, "field-email", errors.message(ApplicationField.EMAIL), keyboard = KeyboardType.Email)
        Field(form.phone, actions.phone, R.string.field_phone, "field-phone", errors.message(ApplicationField.PHONE), keyboard = KeyboardType.Phone)
        Field(form.website, actions.website, R.string.field_website, "field-website", null, keyboard = KeyboardType.Uri, last = true)

        when (state.send) {
            SendState.FAILED -> Error(stringResource(R.string.send_failed))
            SendState.NOT_CONFIGURED -> Error(stringResource(R.string.send_not_configured))
            else -> if (errors.isNotEmpty()) Error(stringResource(R.string.form_has_errors))
        }
        val sending = state.send == SendState.SENDING
        PrimaryButton(
            stringResource(if (sending) R.string.sending else R.string.send),
            actions.submit,
            Modifier.fillMaxWidth().testTag("send"),
            enabled = !sending,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    tag: String,
    error: String?,
    help: String? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = (error ?: help)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else DESCRIPTION_LINES,
        // Next moves the keyboard to the next field; the last field's Done closes it.
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        suffix = suffix?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text).uppercase(), style = GhTextStyles.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Error(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("error"))
}

@Composable
private fun Sent(onBack: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .verticalScroll(rememberScrollState()) // a phone in landscape doesn't fit it all
            .padding(Spacing.screen)
            .testTag("sent"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, Modifier.size(Sizes.iconHero), tint = MaterialTheme.colorScheme.secondary)
        Text(stringResource(R.string.sent_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.sent_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.back_to_map), onBack, Modifier.fillMaxWidth().testTag("back-to-map"))
    }
}

@Composable
private fun Set<ApplicationField>.message(field: ApplicationField): String? {
    if (field !in this) return null
    return when (field) {
        ApplicationField.BUSINESS_NAME -> stringResource(R.string.error_business_name)
        ApplicationField.DESCRIPTION -> stringResource(R.string.error_description, GuideApplication.MIN_DESCRIPTION)
        ApplicationField.ACTIVITY -> stringResource(R.string.error_activity)
        ApplicationField.SPECIES -> stringResource(R.string.error_species)
        ApplicationField.REGION -> stringResource(R.string.error_region)
        ApplicationField.LOCATION -> stringResource(R.string.error_location)
        ApplicationField.PRICE -> stringResource(R.string.error_price)
        ApplicationField.EMAIL -> stringResource(R.string.error_email)
        ApplicationField.PHONE -> stringResource(R.string.error_phone)
    }
}

private const val DESCRIPTION_LINES = 4
