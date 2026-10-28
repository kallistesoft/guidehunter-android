package ca.kallistesoft.guidehunter.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhTextStyles
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing

private val ButtonPadding = PaddingValues(horizontal = Spacing.m)

/** The one main action of a screen, in blaze orange ("Become a guide", "Send"). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text, icon)
    }
}

/** A second action beside the main one ("Cancel"): outlined, in the text colour. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(Sizes.borderEmphasis, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text, icon, style = MaterialTheme.typography.labelLarge)
    }
}

/** A link-like action ("Website"), in green. */
@Composable
fun LinkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        content = content,
    )
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, style: TextStyle = GhTextStyles.button) {
    if (icon != null) {
        Icon(icon, contentDescription = null, Modifier.size(Sizes.iconM))
        Spacer(Modifier.width(Spacing.s))
    }
    // Two lines before "…": a French label ("Envoyer pour vérification") often doesn't fit half a phone's width.
    Text(text, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
}
