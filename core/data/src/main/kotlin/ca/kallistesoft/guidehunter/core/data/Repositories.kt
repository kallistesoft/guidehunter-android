package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Guide
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.InterestRequest
import ca.kallistesoft.guidehunter.core.model.OfferDraft
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.UserContact
import kotlinx.coroutines.flow.Flow

/** The guides on the map. */
interface GuideRepository {
    /** Every published guide's public offer, updated live. Empty while offline with nothing cached. */
    val guides: Flow<List<Guide>>
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

    /** The guide's offers, live, the latest change first, with the boost of the ones on the map. */
    fun offers(uid: String): Flow<List<GuideOffer>>

    /** One of the guide's offers, or null if it's gone. */
    suspend fun offer(uid: String, id: String): GuideOffer?

    /** Creates ([id] null) or edits an offer, back to waiting for review, and emails it to us; returns its id. */
    suspend fun saveOffer(uid: String, id: String?, draft: OfferDraft, profile: GuideProfile): String

    /** Deletes the offer, takes it off the map if it's there, and closes its open requests ("Offer removed"). */
    suspend fun deleteOffer(uid: String, offer: GuideOffer)

    /**
     * Boosts the guide's offer on the map ([GuideOffer.live]) for [ca.kallistesoft.guidehunter.core.model.Boost.DURATION]
     * from now, after they watched an ad; a boost still running starts again from now.
     */
    suspend fun boost(uid: String, offerId: String)

    /** Deletes the profile and every offer, closing their open requests: what deleting the account removes. */
    suspend fun deleteAll(uid: String)
}

/** What a user shares with guides (name, email, phone). Every write throws when it fails (offline, not configured). */
interface UserAccountRepository {
    /** The user's saved contact, live; null before they first save one. */
    fun contact(uid: String): Flow<UserContact?>

    suspend fun saveContact(uid: String, contact: UserContact)

    /** Deletes the saved contact: part of deleting the account. */
    suspend fun deleteAll(uid: String)
}

/**
 * Users' requests to guides ("I'm interested"), one per offer and user, seen by both. Lists are live, newest change
 * first. Every write throws when it fails (offline, not configured, or refused by firestore.rules).
 */
interface RequestRepository {
    /** The requests [uid] sent, as a user. */
    fun sent(uid: String): Flow<List<InterestRequest>>

    /** The requests [uid] received, as a guide. */
    fun received(uid: String): Flow<List<InterestRequest>>

    /** [uid]'s request for one offer, or null if they never sent one. */
    fun request(offerId: String, uid: String): Flow<InterestRequest?>

    /** Sends [uid]'s request for [guide]'s offer, about [trip], with a copy of [contact]: pending. */
    suspend fun send(uid: String, guide: Guide, trip: Trip?, contact: UserContact)

    /** The user cancels their pending or accepted request. */
    suspend fun cancel(id: String)

    /** The user sends a cancelled request again, with their [contact] and [trip] as they are now: pending again. */
    suspend fun resend(id: String, trip: Trip?, contact: UserContact)

    /** The guide accepts a pending request, giving the user their [profile]'s contact. */
    suspend fun approve(id: String, profile: GuideProfile)

    /** The guide declines a pending request. */
    suspend fun reject(id: String)

    /** Closes [guideUid]'s open requests for one offer ([offerId]), or for all their offers (null). */
    suspend fun markOfferRemoved(guideUid: String, offerId: String?)

    /** Deletes the requests [uid] sent: part of deleting the account. */
    suspend fun deleteSent(uid: String)
}

/** The user's location, to show how far a guide is. */
fun interface LocationRepository {
    /** A recent, coarse location; null without the location permission or when Android has none. */
    suspend fun current(): GeoPoint?
}

/** The backend isn't set up in this build (no `google-services.json`): see README, "Firebase". */
class BackendNotConfiguredException : IllegalStateException("Firebase is not configured in this build")
