package ca.kallistesoft.guidehunter.core.model

/**
 * A guide's public offer, shown to everyone on the map. Who the guide is and how to reach them ([GuideContact]) is
 * kept apart and shown only once the user has watched a rewarded ad.
 */
data class Guide(
    val id: String,
    /** What the trip is ("Black bear hunt in Abitibi"), not the guide's name. */
    val title: String,
    val description: String,
    val activity: GuideActivity,
    val species: List<Species>,
    /** Town or region, as the guide wrote it. */
    val region: String,
    val location: GeoPoint,
    /** Starting price per person, in [currency]; null when the guide gives none ("on request"). */
    val priceFrom: Int? = null,
    val currency: String = "CAD",
)

/** The guide's name and contact details, unlocked by a rewarded ad. Blank fields aren't shown. */
data class GuideContact(
    val name: String,
    val phone: String = "",
    val email: String = "",
    val website: String = "",
)

/** What the map shows: guides of a discipline (null for both) offering any of [species] (empty for any species). */
data class GuideFilter(val discipline: Discipline? = null, val species: Set<Species> = emptySet()) {
    fun matches(guide: Guide): Boolean =
        (discipline == null || guide.activity.covers(discipline)) && (species.isEmpty() || guide.species.any { it in species })

    /** The species the picker offers with this filter's discipline. */
    val speciesChoices: List<Species>
        get() = Species.entries.filter { discipline == null || it.discipline == discipline }
}
