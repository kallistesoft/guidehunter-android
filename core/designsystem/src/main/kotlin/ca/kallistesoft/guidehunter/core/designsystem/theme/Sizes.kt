package ca.kallistesoft.guidehunter.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Component sizes: the only place, with [Spacing], where sizes are given in dp. */
object Sizes {
    // Icons
    val iconS = 16.dp
    val iconM = 18.dp
    val iconL = 20.dp
    val iconHero = 72.dp

    // Borders
    val border = 1.dp
    val borderEmphasis = 1.5.dp

    // Touch and buttons
    /** Smallest touch target (round icon buttons). */
    val touchTarget = 44.dp
    val buttonHeight = 52.dp

    // Content
    val cardPadding = 18.dp

    /** A guide's card over the map. */
    val guideCardWidth = 240.dp

    /** The "pick on map" map of the "Become a guide" form. */
    val pickerMapHeight = 220.dp

    /** The width of a sheet or form on a large screen (tablet, landscape): wider is hard to read. */
    val contentMaxWidth = 640.dp
}
