package ca.kallistesoft.guidehunter.core.model

/**
 * What a user shares with a guide when they say they're interested: their name, email and (optional) phone. The email
 * is always the Google account's, so nobody can send requests in someone else's name; only the name and phone are
 * edited.
 */
data class UserContact(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
) {
    /** The fields that keep the contact from being saved; empty when it can be. */
    fun errors(): Set<ContactField> = buildSet {
        if (name.isBlank()) add(ContactField.NAME)
        if (phone.isNotBlank() && phone.count { it.isDigit() } < MIN_PHONE_DIGITS) add(ContactField.PHONE)
    }

    val isComplete: Boolean get() = errors().isEmpty()

    /** The contact as saved: trimmed. */
    fun trimmed() = UserContact(name.trim(), email.trim(), phone.trim())
}

private const val MIN_PHONE_DIGITS = 7

/** A field of the contact form, to show its error. */
enum class ContactField { NAME, PHONE }

/** Where a request stands; both sides see the same one. */
enum class RequestStatus {
    /** Sent: the guide has the user's contact and hasn't answered yet. */
    PENDING,

    /** The guide accepted: the user sees the guide's contact. */
    APPROVED,

    /** The guide declined it: it can't be sent again. */
    REJECTED,

    /** The user cancelled it: they can send it again. */
    CANCELLED,

    /** The guide deleted the offer (or their account). */
    OFFER_REMOVED,
    ;

    /** Still in progress: an offer's deletion closes it. */
    val open: Boolean get() = this == PENDING || this == APPROVED
}

/**
 * A user's "I'm interested" in one offer: one per offer and user ([id] is [requestId]). It keeps a copy of the user's
 * [user] contact as sent, the [trip] they chose (as dates, so each side reads them in its own language), and once
 * the guide accepts, the guide's [guideContact]. [createdAt] and [updatedAt] are epoch milliseconds.
 */
data class InterestRequest(
    val id: String,
    val offerId: String,
    val offerTitle: String,
    val trip: Trip?,
    val guideUid: String,
    val userUid: String,
    val user: UserContact,
    val status: RequestStatus,
    val guideContact: GuideContact? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    companion object {
        /** One request per offer and user: the id says which, so there can't be two. */
        fun requestId(offerId: String, userUid: String) = "${offerId}_$userUid"
    }
}
