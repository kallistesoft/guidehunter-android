package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.ProfileField
import ca.kallistesoft.guidehunter.core.ui.R as UiR

/** The guide's profile: their business and contact, shared by all their offers. [onDone] once saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideProfileScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.send) { if (state.send == SendState.SENT) onDone() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (!state.loading) ProfileForm(state, viewModel)
        }
    }
}

@Composable
private fun ProfileForm(state: GuideProfileUiState, viewModel: GuideProfileViewModel) {
    val form = state.form
    val errors = state.errors
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(stringResource(R.string.profile_intro), style = MaterialTheme.typography.bodyLarge)
        Field(
            form.businessName,
            viewModel::setBusinessName,
            R.string.field_business_name,
            "field-business-name",
            if (ProfileField.BUSINESS_NAME in errors) stringResource(R.string.error_business_name) else null,
        )
        Note(stringResource(R.string.contact_private))
        Field(
            form.email,
            viewModel::setEmail,
            R.string.field_email,
            "field-email",
            if (ProfileField.EMAIL in errors) stringResource(R.string.error_email) else null,
            keyboard = KeyboardType.Email,
        )
        Field(
            form.phone,
            viewModel::setPhone,
            R.string.field_phone,
            "field-phone",
            if (ProfileField.PHONE in errors) stringResource(R.string.error_phone) else null,
            keyboard = KeyboardType.Phone,
        )
        Field(form.website, viewModel::setWebsite, R.string.field_website, "field-website", null, keyboard = KeyboardType.Uri, last = true)
        SendError(state.send, errors.isNotEmpty())
        val sending = state.send == SendState.SENDING
        PrimaryButton(
            stringResource(if (sending) R.string.saving else R.string.save),
            viewModel::submit,
            Modifier.fillMaxWidth().testTag("save"),
            enabled = !sending,
        )
    }
}
