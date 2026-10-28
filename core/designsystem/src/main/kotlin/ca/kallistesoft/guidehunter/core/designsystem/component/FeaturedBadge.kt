package ca.kallistesoft.guidehunter.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ca.kallistesoft.guidehunter.core.designsystem.theme.GhColors
import ca.kallistesoft.guidehunter.core.designsystem.theme.Sizes
import ca.kallistesoft.guidehunter.core.designsystem.theme.Spacing

/**
 * The gold "Featured" pill of a boosted offer (design/mockup/GuideSheet.dc.html): on its card, in its sheet and on
 * the guide's home. Required wherever a boost puts an offer first: it says the place is promoted.
 */
@Composable
fun FeaturedBadge(text: String, modifier: Modifier = Modifier) {
    val colors = GhColors.featured
    Surface(shape = CircleShape, color = colors.tint, contentColor = colors.text, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(Sizes.iconXs))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
