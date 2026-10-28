package ca.kallistesoft.guidehunter.feature.becomeguide

import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.model.OfferStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MyOffersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository()
    private val account = FakeGuideAccountRepository()
    private val offer = GuideOffer("o1", TestGuides.offer)
    private val ads = FakeRewardedAds()
    private val live = GuideOffer("live", TestGuides.offer, OfferStatus.PUBLISHED, live = true)

    private fun viewModel() = MyOffersViewModel(auth, account, ads, { TestGuides.today }, { TestGuides.now }, FakeNativeAds())

    private val MyOffersViewModel.state get() = uiState.value

    @Test
    fun `the guide's profile and offers, live`() = runTest {
        auth.user.value = TestGuides.user
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertFalse(vm.state.loading)
        assertEquals(TestGuides.user, vm.state.user)
        assertNull(vm.state.profile)
        assertTrue(vm.state.offers.isEmpty())
        assertEquals(TestGuides.today, vm.state.today)

        account.profiles.value = mapOf(uid to TestGuides.profile)
        account.give(uid, offer)
        assertEquals(TestGuides.profile, vm.state.profile)
        assertEquals(listOf(offer), vm.state.offers)
    }

    /** Signed in, with [live] on the map, the state collected. */
    private fun TestScope.signedInWithLiveOffer(): MyOffersViewModel {
        auth.user.value = TestGuides.user
        account.give(uid, live)
        return viewModel().also { collecting(it.uiState) }
    }

    @Test
    fun `an ad watched to the end features the offer for 3 days`() = runTest {
        val vm = signedInWithLiveOffer()
        assertEquals(TestGuides.now, vm.state.now)
        vm.prepareBoost()
        assertEquals("the ad loads with the explanation", 1, ads.preloads)
        vm.boost("live")
        assertEquals(1, ads.shown)
        assertNull(vm.state.boost)
        assertEquals(TestGuides.now + 3.days, vm.state.offers.single().boostedUntil)
    }

    @Test
    fun `with no ad to show, the boost is given`() = runTest {
        val vm = signedInWithLiveOffer()
        ads.outcome = RewardOutcome.NO_ADS
        vm.boost("live")
        assertTrue(vm.state.offers.single().isBoosted(TestGuides.now))
    }

    @Test
    fun `an ad closed early or offline leaves the offer as it was, and says why`() = runTest {
        val vm = signedInWithLiveOffer()
        ads.outcome = RewardOutcome.NOT_EARNED
        vm.boost("live")
        assertEquals(BoostAttempt("live", BoostError.NOT_EARNED), vm.state.boost)
        ads.outcome = RewardOutcome.OFFLINE
        vm.boost("live")
        assertEquals(BoostAttempt("live", BoostError.OFFLINE), vm.state.boost)
        assertNull(vm.state.offers.single().boostedUntil)
    }

    @Test
    fun `a boost that can't be saved says so, and can be tried again`() = runTest {
        val vm = signedInWithLiveOffer()
        account.failure = IllegalStateException("offline")
        vm.boost("live")
        assertEquals(BoostAttempt("live", BoostError.FAILED), vm.state.boost)
        account.failure = null
        vm.boost("live")
        assertNull(vm.state.boost)
        assertEquals(2, ads.shown)
        assertTrue(vm.state.offers.single().isBoosted(TestGuides.now))
    }

    @Test
    fun `one boost at a time while the ad shows`() = runTest {
        val vm = signedInWithLiveOffer()
        ads.onShow = CompletableDeferred()
        vm.boost("live")
        assertEquals(BoostAttempt("live"), vm.state.boost)
        vm.boost("live")
        assertEquals("no second ad", 1, ads.shown)
        ads.onShow?.complete(Unit)
        assertNull(vm.state.boost)
    }

    @Test
    fun `signed out, there's nothing to boost`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.boost("live")
        assertEquals(0, ads.shown)
    }

    @Test
    fun `signed out, it waits (the app leaves guide mode)`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertTrue(vm.state.loading)
        assertNull(vm.state.user)
    }
}
