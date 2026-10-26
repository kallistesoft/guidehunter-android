package ca.kallistesoft.guidehunter.core.ui

import androidx.annotation.StringRes
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GuideActivity
import ca.kallistesoft.guidehunter.core.model.Species

/** The name of a species, as the pickers and a guide's offer show it. */
@get:StringRes
val Species.label: Int
    get() = when (this) {
        Species.BLACK_BEAR -> R.string.species_black_bear
        Species.MOOSE -> R.string.species_moose
        Species.WHITE_TAILED_DEER -> R.string.species_white_tailed_deer
        Species.CARIBOU -> R.string.species_caribou
        Species.WILD_TURKEY -> R.string.species_wild_turkey
        Species.WATERFOWL -> R.string.species_waterfowl
        Species.UPLAND_BIRDS -> R.string.species_upland_birds
        Species.PREDATORS -> R.string.species_predators
        Species.WALLEYE -> R.string.species_walleye
        Species.NORTHERN_PIKE -> R.string.species_northern_pike
        Species.BROOK_TROUT -> R.string.species_brook_trout
        Species.LAKE_TROUT -> R.string.species_lake_trout
        Species.ATLANTIC_SALMON -> R.string.species_atlantic_salmon
        Species.SMALLMOUTH_BASS -> R.string.species_smallmouth_bass
        Species.MUSKELLUNGE -> R.string.species_muskellunge
        Species.ICE_FISHING -> R.string.species_ice_fishing
    }

@get:StringRes
val Discipline.label: Int
    get() = when (this) {
        Discipline.HUNTING -> R.string.discipline_hunting
        Discipline.FISHING -> R.string.discipline_fishing
    }

@get:StringRes
val GuideActivity.label: Int
    get() = when (this) {
        GuideActivity.HUNT -> R.string.activity_hunt
        GuideActivity.FISH -> R.string.activity_fish
        GuideActivity.BOTH -> R.string.activity_both
    }
