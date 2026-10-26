package ca.kallistesoft.guidehunter.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// BetterHunter's "field guide" palette (its design/README.md): forest greens, blaze orange for the one main
// action and the current selection, green for links and fishing.
//
// Roles: primary = blaze orange (main action, selection), primaryContainer = selected chip; secondary = green
// (links, fishing), secondaryContainer = green tint; tertiary = orange label text,
// tertiaryContainer = orange tint; surfaceContainerLow = card; outlineVariant = card border; outline = field border;
// surfaceContainerHighest = empty track.

/**
 * The map's pins (design/README.md): orange for hunting, teal for fishing, gold for both. They differ in lightness
 * too, not only in hue, and are outlined in the background colour so they stand out on the map.
 */
data class PinColors(val hunt: Color, val fish: Color, val both: Color, val outline: Color)

val LightPinColors = PinColors(hunt = Color(0xFFE8691E), fish = Color(0xFF1F7A6A), both = Color(0xFFC99A12), outline = Color(0xFFFFFFFF))
val DarkPinColors = PinColors(hunt = Color(0xFFF27B2B), fish = Color(0xFF4FB3A0), both = Color(0xFFE9C25A), outline = Color(0xFF0E1611))

internal val LightColors = lightColorScheme(
    primary = Color(0xFFE8691E),
    onPrimary = Color(0xFF1A0E05),
    primaryContainer = Color(0xFFFDEBDD),
    onPrimaryContainer = Color(0xFF17201A),
    inversePrimary = Color(0xFFF27B2B),
    secondary = Color(0xFF2F6B3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EFE4),
    onSecondaryContainer = Color(0xFF2F6B3E),
    tertiary = Color(0xFFB4500F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE6D4),
    onTertiaryContainer = Color(0xFFB4500F),
    background = Color(0xFFF5F2EA),
    onBackground = Color(0xFF17201A),
    surface = Color(0xFFF5F2EA),
    onSurface = Color(0xFF17201A),
    surfaceVariant = Color(0xFFEDE8DC),
    onSurfaceVariant = Color(0xFF5B6659),
    surfaceTint = Color(0xFFF5F2EA),
    inverseSurface = Color(0xFF17201A),
    inverseOnSurface = Color(0xFFEEF1EA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDA),
    onErrorContainer = Color(0xFF8C1D18),
    outline = Color(0xFFDDD7C8),
    outlineVariant = Color(0xFFE6E1D5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE2DCCD),
    surfaceDim = Color(0xFFEDE8DC),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFF27B2B),
    onPrimary = Color(0xFF1A0E05),
    primaryContainer = Color(0xFF2A1C11),
    onPrimaryContainer = Color(0xFFEEF1EA),
    inversePrimary = Color(0xFFE8691E),
    secondary = Color(0xFF8FD19E),
    onSecondary = Color(0xFF0E1611),
    secondaryContainer = Color(0xFF22382A),
    onSecondaryContainer = Color(0xFF8FD19E),
    tertiary = Color(0xFFF7A06A),
    onTertiary = Color(0xFF1A0E05),
    tertiaryContainer = Color(0xFF3A2516),
    onTertiaryContainer = Color(0xFFF7A06A),
    background = Color(0xFF0E1611),
    onBackground = Color(0xFFEEF1EA),
    surface = Color(0xFF0E1611),
    onSurface = Color(0xFFEEF1EA),
    surfaceVariant = Color(0xFF24352A),
    onSurfaceVariant = Color(0xFFA9B5AB),
    surfaceTint = Color(0xFF0E1611),
    inverseSurface = Color(0xFFEEF1EA),
    inverseOnSurface = Color(0xFF17201A),
    error = Color(0xFFF2867A),
    onError = Color(0xFF1A0E05),
    errorContainer = Color(0xFF3A1A16),
    onErrorContainer = Color(0xFFF9C9C2),
    outline = Color(0xFF2C3B30),
    outlineVariant = Color(0xFF24352A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF24352A),
    surfaceContainerLowest = Color(0xFF0E1611),
    surfaceContainerLow = Color(0xFF17221A),
    surfaceContainer = Color(0xFF17221A),
    surfaceContainerHigh = Color(0xFF1C2920),
    surfaceContainerHighest = Color(0xFF24352A),
    surfaceDim = Color(0xFF0E1611),
)
