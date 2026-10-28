package ca.kallistesoft.guidehunter.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GuideFilter
import ca.kallistesoft.guidehunter.core.model.Species
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SpeciesFilterUiState(
    val loading: Boolean = true,
    /** The species listed: those of the map's discipline, or all of them. */
    val choices: List<Species> = emptyList(),
    /** The chosen ones (none: any species). */
    val chosen: Set<Species> = emptySet(),
)

/**
 * The species the map shows, a setting kept for next time ([SettingsRepository.setSpeciesFilter]); each tap is saved
 * at once (Pattern A of .claude/skills/viewmodel-state). From the map, [discipline] is the one chosen there and only its
 * species are listed; from Settings (null), all of them.
 */
@HiltViewModel(assistedFactory = SpeciesFilterViewModel.Factory::class)
class SpeciesFilterViewModel @AssistedInject constructor(
    @Assisted private val discipline: Discipline?,
    private val settings: SettingsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(discipline: Discipline?): SpeciesFilterViewModel
    }

    private val choices = GuideFilter(discipline).speciesChoices

    val uiState: StateFlow<SpeciesFilterUiState> = settings.settings
        .map { SpeciesFilterUiState(loading = false, choices = choices, chosen = it.speciesFilter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeciesFilterUiState())

    /** One write at a time: two quick taps each read what the other wrote. */
    private val writing = Mutex()

    /** Adds [species] to the chosen ones, or removes it: the map shows guides offering any of them. */
    fun toggle(species: Species) = save { chosen -> if (species in chosen) chosen - species else chosen + species }

    /** Any species again. */
    fun clear() = save { emptySet() }

    /**
     * Saves the change even if the screen closes right after the tap ("Show on the map"): the ViewModel is cleared then,
     * and a cancelled write would lose the last tap.
     */
    private fun save(change: (Set<Species>) -> Set<Species>) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                writing.withLock { settings.setSpeciesFilter(change(settings.settings.first().speciesFilter)) }
            }
        }
    }
}
