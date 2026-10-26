package ca.kallistesoft.guidehunter.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ca.kallistesoft.guidehunter.core.designsystem.R

/** Body text: Barlow 400/500/600. Bundled (SIL Open Font License, `assets/licenses/barlow-OFL.txt`). */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Headings and numbers: Barlow Condensed 600/700. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private val heading = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold)
private val body = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal)

/** Material 3 type scale on the mockup's fonts: condensed headings, Barlow for everything people read. */
internal val AppTypography = Typography(
    displayLarge = heading.copy(fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 60.sp),
    displayMedium = heading.copy(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 48.sp),
    displaySmall = heading.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp),
    headlineLarge = heading.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = heading.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineSmall = heading.copy(fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = heading.copy(fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = heading.copy(fontSize = 20.sp, lineHeight = 24.sp),
    titleSmall = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = body.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = body.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

/** Text styles the M3 scale has no slot for. */
object GhTextStyles {
    /** Small uppercase label above a title or a section ("SPECIES", "CONTACT"). */
    val overline = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)

    /** Text of the main button of a screen. */
    val button = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)

    /** The app name in the map's header. */
    val brand = heading.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.2.sp)
}
