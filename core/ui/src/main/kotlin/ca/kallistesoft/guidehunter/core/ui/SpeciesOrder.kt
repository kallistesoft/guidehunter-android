package ca.kallistesoft.guidehunter.core.ui

import android.content.Context
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.Species
import java.text.Collator

/**
 * [species] as the pickers list them: hunting, then fishing, each in the alphabetical order of its labels in the app's
 * language (so the order differs between English and French). Disciplines without any of [species] are left out.
 */
fun Context.speciesByDiscipline(species: Collection<Species>): List<Pair<Discipline, List<Species>>> {
    // A collator, not String order: "Élan" sorts with the E's, and case is ignored.
    val collator = Collator.getInstance(resources.configuration.locales[0])
    return Discipline.entries.mapNotNull { discipline ->
        species.filter { it.discipline == discipline }
            .sortedWith(compareBy(collator) { getString(it.label) })
            .takeIf { it.isNotEmpty() }
            ?.let { discipline to it }
    }
}
