package ca.kallistesoft.guidehunter.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Destinations. Serializable so the back stack survives configuration changes and process death.

/** The first launch: sign in with Google, or "Later". */
@Serializable
data object Welcome : NavKey

/** After the first sign-in: user or guide. */
@Serializable
data object ChooseMode : NavKey

/** The map of guides, the home in user mode. */
@Serializable
data object GuideMap : NavKey

/** The guide's profile and offers, the home in guide mode. */
@Serializable
data object MyOffers : NavKey

/** The settings of either mode. */
@Serializable
data object Settings : NavKey

/** The guide's profile (business and contact). */
@Serializable
data object GuideProfile : NavKey

/** A guide's offer: a new one ([offerId] null) or one sent before. */
@Serializable
data class OfferEditor(val offerId: String? = null) : NavKey
