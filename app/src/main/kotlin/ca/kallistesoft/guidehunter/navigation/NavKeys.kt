package ca.kallistesoft.guidehunter.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Destinations. Serializable so the back stack survives configuration changes and process death.

/** The map of guides, the home screen. */
@Serializable
data object GuideMap : NavKey

/** The "Become a guide" form. */
@Serializable
data object BecomeGuide : NavKey
