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
    /** Smallest touch target (round icon buttons), Android's minimum. */
    val touchTarget = 48.dp
    val buttonHeight = 52.dp

    // Content
    val cardPadding = 18.dp

    /** A guide's card over the map, and the height the cards and their count take at the map's bottom. */
    val guideCardWidth = 240.dp
    val guideCardsInset = 120.dp

    /** The grey bars where a guide's hidden name and contact will be. */
    val placeholderLine = 14.dp
    val placeholderLineSmall = 10.dp

    /** The "pick on map" map of the "Become a guide" form. */
    val pickerMapHeight = 220.dp

    /** The width of a sheet or form on a large screen (tablet, landscape): wider is hard to read. */
    val contentMaxWidth = 640.dp

    /** From this window width (a tablet, an unfolded foldable) a guide's offer opens beside the map, not over it. */
    val sidePaneMinWindowWidth = 840.dp
    val sidePaneWidth = 400.dp
}
