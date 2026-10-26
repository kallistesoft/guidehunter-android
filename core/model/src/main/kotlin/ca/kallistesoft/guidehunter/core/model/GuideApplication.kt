package ca.kallistesoft.guidehunter.core.model

/** A guide's "Become a guide" form, sent to us for review before the offer goes on the map. */
data class GuideApplication(
    val businessName: String = "",
    val description: String = "",
    val activity: GuideActivity? = null,
    val species: Set<Species> = emptySet(),
    val region: String = "",
    val location: GeoPoint? = null,
    /** Starting price per person, as typed (digits only once valid); blank for "on request". */
    val priceFrom: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
) {
    /** The fields that keep the form from being sent; empty when it can be. */
    fun errors(): Set<ApplicationField> = buildSet {
        if (businessName.isBlank()) add(ApplicationField.BUSINESS_NAME)
        if (description.trim().length < MIN_DESCRIPTION) add(ApplicationField.DESCRIPTION)
        if (activity == null) add(ApplicationField.ACTIVITY)
        if (species.isEmpty() || activity != null && species.any { !activity.covers(it.discipline) }) {
            add(ApplicationField.SPECIES)
        }
        if (region.isBlank()) add(ApplicationField.REGION)
        if (location == null) add(ApplicationField.LOCATION)
        if (priceFrom.isNotBlank() && priceFrom.trim().toIntOrNull()?.takeIf { it > 0 } == null) {
            add(ApplicationField.PRICE)
        }
        if (!EMAIL.matches(email.trim())) add(ApplicationField.EMAIL)
        if (phone.isNotBlank() && phone.count { it.isDigit() } < MIN_PHONE_DIGITS) add(ApplicationField.PHONE)
    }

    companion object {
        /** A description says enough for a hunter or angler to choose: a sentence or two at least. */
        const val MIN_DESCRIPTION = 30
        const val MAX_DESCRIPTION = 2_000
        private const val MIN_PHONE_DIGITS = 7
        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}

/** A field of the "Become a guide" form, to show its error. */
enum class ApplicationField { BUSINESS_NAME, DESCRIPTION, ACTIVITY, SPECIES, REGION, LOCATION, PRICE, EMAIL, PHONE }
