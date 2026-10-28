package ca.kallistesoft.guidehunter.core.model

/** One account does both: looking for a guide (the map) or being one (the guide's offers and requests). */
enum class AppMode { USER, GUIDE }

/** What the app remembers on this phone, signed in or not. */
data class AppSettings(
    /** The first launch's welcome is done (signed in or "Later"). */
    val onboarded: Boolean = false,
    /** The home the app opens on; guide mode only with an account. */
    val mode: AppMode = AppMode.USER,
    /** The species the map shows (none: any species), chosen on the map or in Settings and kept for next time. */
    val speciesFilter: Set<Species> = emptySet(),
)
