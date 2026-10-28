package ca.kallistesoft.guidehunter.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/** The account error, if any, with OK to dismiss it. */
@Composable
internal fun AccountErrorText(error: AccountError?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (error == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (error) {
                        AccountError.SIGN_IN_FAILED -> R.string.sign_in_failed
                        AccountError.NOT_CONFIGURED -> R.string.sign_in_not_configured
                        AccountError.DELETE_FAILED -> R.string.delete_account_failed
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("account-error"),
            )
        }
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-error")) { Text(stringResource(R.string.dismiss)) }
    }
}
