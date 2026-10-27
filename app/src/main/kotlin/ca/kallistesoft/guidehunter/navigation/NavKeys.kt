package ca.kallistesoft.guidehunter.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Destinations. Serializable so the back stack survives configuration changes and process death.

/** The map of guides, the home screen. */
@Serializable
data object GuideMap : NavKey

/** "Become a guide": Google sign-in, then the guide's profile and offers. */
@Serializable
data object MyOffers : NavKey

/** The guide's profile (business and contact). */
@Serializable
data object GuideProfile : NavKey

/** A guide's offer: a new one ([offerId] null) or one sent before. */
@Serializable
data class OfferEditor(val offerId: String? = null) : NavKey
