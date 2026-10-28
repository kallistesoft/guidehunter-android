package ca.kallistesoft.guidehunter.feature.becomeguide

import ca.kallistesoft.guidehunter.core.model.GuideContact
import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import ca.kallistesoft.guidehunter.core.testing.FakeGuideAccountRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RequestsReceivedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val requests = FakeRequestRepository()
    private val account = FakeGuideAccountRepository()
    private val pending = TestGuides.request.copy(id = "bear_u2", guideUid = uid, userUid = "u2")
    private val answered = pending.copy(id = "bear_u3", userUid = "u3", status = RequestStatus.CANCELLED)

    private fun viewModel() = RequestsReceivedViewModel(auth, requests, account, FakeNativeAds())

    private val RequestsReceivedViewModel.state get() = uiState.value

    @Test
    fun `the pending requests apart from the answered ones, only the guide's`() = runTest {
        requests.give(pending, answered, TestGuides.request)
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertEquals(listOf(pending), vm.state.pending)
        assertEquals(listOf(answered), vm.state.answered)
        auth.user.value = null
        assertFalse(vm.state.loading)
        assertTrue(vm.state.pending.isEmpty())
    }

    @Test
    fun `accepting gives the user the guide's profile contact`() = runTest {
        requests.give(pending)
        account.profiles.value = mapOf(uid to TestGuides.profile)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.accept(pending.id)
        val accepted = requests.requests.value.getValue(pending.id)
        assertEquals(RequestStatus.APPROVED, accepted.status)
        assertEquals(GuideContact(TestGuides.profile.businessName, TestGuides.profile.phone, TestGuides.profile.email), accepted.guideContact)
        assertEquals(listOf(accepted), vm.state.answered)
        assertNull(vm.state.error)
    }

    @Test
    fun `accepting without a profile asks for one first`() = runTest {
        requests.give(pending)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.accept(pending.id)
        assertEquals(ReceivedError.NEEDS_PROFILE, vm.state.error)
        assertEquals(RequestStatus.PENDING, requests.requests.value.getValue(pending.id).status)
        vm.dismissError()
        assertNull(vm.state.error)
    }

    @Test
    fun `declining, and a failure says so, a second tap while one goes does nothing`() = runTest {
        requests.give(pending)
        val vm = viewModel()
        collecting(vm.uiState)
        requests.failure = IllegalStateException("offline")
        vm.decline(pending.id)
        assertEquals(ReceivedError.FAILED, vm.state.error)
        requests.failure = null
        requests.onWrite = CompletableDeferred()
        vm.decline(pending.id)
        assertTrue(vm.state.busy)
        vm.accept(pending.id)
        requests.onWrite?.complete(Unit)
        assertEquals(RequestStatus.REJECTED, requests.requests.value.getValue(pending.id).status)
        assertNull(vm.state.error)
    }
}
