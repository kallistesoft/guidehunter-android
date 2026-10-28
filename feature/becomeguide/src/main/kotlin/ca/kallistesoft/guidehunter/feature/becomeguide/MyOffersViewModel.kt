package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.Today
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class MyOffersUiState(
    /** The guide and their offers aren't known yet. */
    val loading: Boolean = true,
    /** The signed-in guide (guide mode needs an account: the app leaves it when signed out). */
    val user: GuideUser? = null,
    /** Their profile; null until they first save one. */
    val profile: GuideProfile? = null,
    val offers: List<GuideOffer> = emptyList(),
    /** Offers whose dates are all over show as off the map. */
    val today: LocalDate = LocalDate.fromEpochDays(0),
)

/**
 * The guide's home: their profile and offers, live (Pattern A of .claude/skills/viewmodel-state). Signing in and out
 * is in the settings. [nativeAds] serves the ad card below the offers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyOffersViewModel @Inject constructor(
    auth: AuthRepository,
    private val account: GuideAccountRepository,
    private val today: Today,
    val nativeAds: NativeAds,
) : ViewModel() {
    val uiState: StateFlow<MyOffersUiState> = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(MyOffersUiState())
        } else {
            combine(account.profile(user.uid), account.offers(user.uid)) { profile, offers ->
                MyOffersUiState(loading = false, user = user, profile = profile, offers = offers, today = today())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyOffersUiState())
}
