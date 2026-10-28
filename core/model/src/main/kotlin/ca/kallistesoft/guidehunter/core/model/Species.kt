package ca.kallistesoft.guidehunter.core.model

/** Hunting or fishing: what a species is pursued for, and what a search can be narrowed to. */
enum class Discipline { HUNTING, FISHING }

/** What a guide offers: hunting trips, fishing trips, or both. */
enum class GuideActivity(val disciplines: Set<Discipline>) {
    HUNT(setOf(Discipline.HUNTING)),
    FISH(setOf(Discipline.FISHING)),
    BOTH(setOf(Discipline.HUNTING, Discipline.FISHING)),
    ;

    fun covers(discipline: Discipline): Boolean = discipline in disciplines
}

/**
 * The species a guide can offer, the list of the form's and the search's species pickers. Stored by [name] in
 * Firestore and listed in firestore.rules, so a constant is never renamed; labels come from the app's strings.
 */
enum class Species(val discipline: Discipline) {
    BLACK_BEAR(Discipline.HUNTING),
    MOOSE(Discipline.HUNTING),
    WHITE_TAILED_DEER(Discipline.HUNTING),
    MULE_DEER(Discipline.HUNTING),
    ELK(Discipline.HUNTING),
    CARIBOU(Discipline.HUNTING),
    PRONGHORN(Discipline.HUNTING),
    BIGHORN_SHEEP(Discipline.HUNTING),
    MOUNTAIN_GOAT(Discipline.HUNTING),
    WILD_TURKEY(Discipline.HUNTING),
    WATERFOWL(Discipline.HUNTING),
    UPLAND_BIRDS(Discipline.HUNTING),
    SHARP_TAILED_GROUSE(Discipline.HUNTING),
    PHEASANT(Discipline.HUNTING),
    PREDATORS(Discipline.HUNTING),
    WALLEYE(Discipline.FISHING),
    YELLOW_PERCH(Discipline.FISHING),
    NORTHERN_PIKE(Discipline.FISHING),
    BROOK_TROUT(Discipline.FISHING),
    RAINBOW_TROUT(Discipline.FISHING),
    LAKE_TROUT(Discipline.FISHING),
    ARCTIC_CHAR(Discipline.FISHING),
    ATLANTIC_SALMON(Discipline.FISHING),
    PACIFIC_SALMON(Discipline.FISHING),
    SMALLMOUTH_BASS(Discipline.FISHING),
    LARGEMOUTH_BASS(Discipline.FISHING),
    MUSKELLUNGE(Discipline.FISHING),
    STURGEON(Discipline.FISHING),
    ICE_FISHING(Discipline.FISHING),
    ;

    companion object {
        /** The species a guide offering [activity] can pick, in the list's order. */
        fun forActivity(activity: GuideActivity): List<Species> = entries.filter { activity.covers(it.discipline) }

        /** The species stored as [names]; unknown names (a species removed since) are dropped. */
        fun fromNames(names: Collection<String>): List<Species> = names.mapNotNull { name -> entries.find { it.name == name } }
    }
}
