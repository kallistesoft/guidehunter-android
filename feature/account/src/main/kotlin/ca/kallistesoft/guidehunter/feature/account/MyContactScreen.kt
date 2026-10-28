package ca.kallistesoft.guidehunter.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.kallistesoft.guidehunter.core.designsystem.component.PrimaryButton
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing
import ca.kallistesoft.guidehunter.core.model.ContactField
import ca.kallistesoft.guidehunter.core.ui.R as UiR

/** "My contact info": what a guide gets when the user is interested. [onDone] once saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyContactScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyContactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.save) { if (state.save == SaveState.SAVED) onDone() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.my_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (!state.loading) ContactForm(state, viewModel)
        }
    }
}

@Composable
private fun ContactForm(state: MyContactUiState, viewModel: MyContactViewModel) {
    val form = state.form
    Column(
        Modifier
            .widthIn(max = Sizes.contentMaxWidth)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l)
            .testTag("my-contact-screen"),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(stringResource(R.string.my_contact_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = { Text(stringResource(R.string.field_name)) },
            isError = ContactField.NAME in state.errors,
            supportingText = if (ContactField.NAME in state.errors) ({ Text(stringResource(R.string.error_name)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("field-name"),
        )
        OutlinedTextField(
            value = form.email,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_email)) },
            trailingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            supportingText = { Text(stringResource(R.string.email_from_google)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-email"),
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = viewModel::setPhone,
            label = { Text(stringResource(R.string.field_phone)) },
            isError = ContactField.PHONE in state.errors,
            supportingText = if (ContactField.PHONE in state.errors) ({ Text(stringResource(R.string.error_phone)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("field-phone"),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.my_contact_copy_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (state.save) {
            SaveState.FAILED -> SaveError(R.string.save_failed)
            SaveState.NOT_CONFIGURED -> SaveError(R.string.save_not_configured)
            else -> Unit
        }
        val saving = state.save == SaveState.SAVING
        PrimaryButton(
            stringResource(if (saving) R.string.saving else R.string.save),
            viewModel::submit,
            Modifier.fillMaxWidth().testTag("save"),
            enabled = !saving,
        )
    }
}

@Composable
private fun SaveError(text: Int) {
    Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("save-error"))
}
