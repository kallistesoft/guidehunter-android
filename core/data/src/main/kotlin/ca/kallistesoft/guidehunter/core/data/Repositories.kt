package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import kotlinx.coroutines.flow.Flow

/** The guides on the map. */
interface GuideRepository {
    /** Every published guide's public offer, updated live. Empty while offline with nothing cached. */
    val guides: Flow<List<Guide>>

    /** The guide's name and contact details, read only once the user has earned them; null if it has none. */
    suspend fun contact(guideId: String): GuideContact?
}

/** The guide signed in with Google (Firebase Auth). Every call throws when it fails (offline, not configured). */
interface AuthRepository {
    /** The signed-in guide, null when signed out; updated on sign-in and sign-out. */
    val user: Flow<GuideUser?>

    /** Signs in with the ID token Google's sign-in sheet gave. */
    suspend fun signIn(googleIdToken: String)

    /** Proves again who the guide is (Firebase asks it before deleting an account), with a fresh Google token. */
    suspend fun reauthenticate(googleIdToken: String)

    suspend fun signOut()

    /** Deletes the Firebase account itself; delete the guide's data first, while still allowed to. */
    suspend fun deleteUser()
}

/**
 * A guide's profile and offers. Offers are sent to us by email for review, and every edit is reviewed again: the map
 * keeps the reviewed version until then. Every call throws when it fails (offline, not configured).
 */
interface GuideAccountRepository {
    /** The guide's profile, live; null before they first save one. */
    fun profile(uid: String): Flow<GuideProfile?>

    /** Saves the profile, and updates the contact shown with each of the guide's offers on the map. */
    suspend fun saveProfile(uid: String, profile: GuideProfile)

    /** The guide's offers, live, the latest change first. */
    fun offers(uid: String): Flow<List<GuideOffer>>

    /** One of the guide's offers, or null if it's gone. */
    suspend fun offer(uid: String, id: String): GuideOffer?

    /** Creates ([id] null) or edits an offer, back to waiting for review, and emails it to us; returns its id. */
    suspend fun saveOffer(uid: String, id: String?, draft: OfferDraft, profile: GuideProfile): String

    /** Deletes the offer, and takes it off the map if it's there. */
    suspend fun deleteOffer(uid: String, offer: GuideOffer)

    /** Deletes the profile and every offer: what deleting the account removes. */
    suspend fun deleteAll(uid: String)
}

/** The user's location, to show how far a guide is. */
fun interface LocationRepository {
    /** A recent, coarse location; null without the location permission or when Android has none. */
    suspend fun current(): GeoPoint?
}

/** The backend isn't set up in this build (no `google-services.json`): see README, "Firebase". */
class BackendNotConfiguredException : IllegalStateException("Firebase is not configured in this build")
