package ca.kallistesoft.guidehunter.core.model

/** Who is signed in as a guide: their Firebase account, from Google sign-in. */
data class GuideUser(val uid: String, val email: String = "", val name: String = "")

/**
 * What a guide's offers share: the business and how to reach it. Private: shown with an offer only once a user has
 * watched a rewarded ad (as its [GuideContact]).
 */
data class GuideProfile(
    val businessName: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
) {
    /** The fields that keep the profile from being saved; empty when it can be. */
    fun errors(): Set<ProfileField> = buildSet {
        if (businessName.isBlank()) add(ProfileField.BUSINESS_NAME)
        if (!EMAIL.matches(email.trim())) add(ProfileField.EMAIL)
        if (phone.isNotBlank() && phone.count { it.isDigit() } < MIN_PHONE_DIGITS) add(ProfileField.PHONE)
    }

    val isComplete: Boolean get() = errors().isEmpty()

    /** The profile as saved: trimmed. */
    fun trimmed() = GuideProfile(businessName.trim(), email.trim(), phone.trim(), website.trim())
}

private const val MIN_PHONE_DIGITS = 7
private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

/** A field of the profile form, to show its error. */
enum class ProfileField { BUSINESS_NAME, EMAIL, PHONE }
