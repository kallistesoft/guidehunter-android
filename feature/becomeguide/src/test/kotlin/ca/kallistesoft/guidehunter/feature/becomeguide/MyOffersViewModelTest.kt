package ca.kallistesoft.guidehunter.feature.becomeguide

import ca.kallistesoft.guidehunter.core.model.GuideOffer
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
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

    private fun viewModel() = MyOffersViewModel(auth, account, { TestGuides.today })

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

    @Test
    fun `signed out, it waits (the app leaves guide mode)`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertTrue(vm.state.loading)
        assertNull(vm.state.user)
    }
}
