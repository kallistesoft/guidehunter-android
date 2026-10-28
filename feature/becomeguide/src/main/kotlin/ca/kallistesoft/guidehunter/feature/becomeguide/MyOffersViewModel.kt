package ca.kallistesoft.guidehunter.feature.becomeguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kallistesoft.guidehunter.core.ads.NativeAds
import ca.kallistesoft.guidehunter.core.ads.BoostAds
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.ads.RewardedAds
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.GuideProfile
import ca.kallistesoft.guidehunter.core.model.GuideUser
import ca.kallistesoft.guidehunter.core.model.Now
import ca.kallistesoft.guidehunter.core.model.Today
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /** When the offers were read: whether their boost is running, and whether they can be boosted again. */
    val now: Instant = Instant.fromEpochSeconds(0),
    /** A boost under way (the ad showing) or that failed; null otherwise. */
    val boost: BoostAttempt? = null,
)

/** The guide boosting [offerId]: the ad is showing or the boost saving, or it failed with [error]. */
data class BoostAttempt(val offerId: String, val error: BoostError? = null)

enum class BoostError {
    /** The ad was closed before the end: no boost. */
    NOT_EARNED,

    /** No network: no ad, no boost. */
    OFFLINE,

    /** The ad was watched but the boost couldn't be saved. */
    FAILED,
}

/**
 * The guide's home: their profile and offers, live (Pattern A of .claude/skills/viewmodel-state), and the boost of an
 * offer on the map: a rewarded ad ([BoostAds]), then [GuideAccountRepository.boost]. Signing in and out is in the
 * settings. [nativeAds] serves the ad card below the offers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyOffersViewModel @Inject constructor(
    auth: AuthRepository,
    private val account: GuideAccountRepository,
    @param:BoostAds private val ads: RewardedAds,
    private val today: Today,
    private val now: Now,
    val nativeAds: NativeAds,
) : ViewModel() {
    private val boost = MutableStateFlow<BoostAttempt?>(null)

    val uiState: StateFlow<MyOffersUiState> = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(MyOffersUiState())
        } else {
            combine(account.profile(user.uid), account.offers(user.uid), boost) { profile, offers, boost ->
                MyOffersUiState(
                    loading = false,
                    user = user,
                    profile = profile,
                    offers = offers,
                    today = today(),
                    now = now(),
                    boost = boost,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyOffersUiState())

    /** The boost's explanation opened: its ad starts loading, so it shows without waiting. */
    fun prepareBoost() = ads.preload()

    /** "Watch an ad to boost": the ad, then the offer featured for 3 days if it was watched to the end. */
    fun boost(offerId: String) {
        val uid = uiState.value.user?.uid ?: return
        if (boost.value?.error == null && boost.value != null) return // one at a time
        boost.value = BoostAttempt(offerId)
        viewModelScope.launch {
            boost.value = when (ads.showForReward()) {
                RewardOutcome.EARNED, RewardOutcome.NO_ADS -> save(uid, offerId)
                RewardOutcome.NOT_EARNED -> BoostAttempt(offerId, BoostError.NOT_EARNED)
                RewardOutcome.OFFLINE -> BoostAttempt(offerId, BoostError.OFFLINE)
            }
        }
    }

    /** Null once saved: the offer itself then says until when it's featured. */
    private suspend fun save(uid: String, offerId: String): BoostAttempt? = try {
        account.boost(uid, offerId)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        BoostAttempt(offerId, BoostError.FAILED)
    }
}
