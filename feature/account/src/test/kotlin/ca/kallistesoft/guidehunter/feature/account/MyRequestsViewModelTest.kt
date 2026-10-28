package ca.kallistesoft.guidehunter.feature.account

import ca.kallistesoft.guidehunter.core.model.RequestStatus
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeNativeAds
import ca.kallistesoft.guidehunter.core.testing.FakeRequestRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
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

class MyRequestsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val requests = FakeRequestRepository()
    private val users = FakeUserAccountRepository()
    private val cancelled = TestGuides.request.copy(id = "salmon_$uid", offerId = "salmon", status = RequestStatus.CANCELLED, updatedAt = 5)

    private fun viewModel() = MyRequestsViewModel(auth, requests, users, FakeNativeAds())

    private val MyRequestsViewModel.state get() = uiState.value

    @Test
    fun `the user's requests, the latest change first, and only theirs`() = runTest {
        requests.give(TestGuides.request, cancelled, TestGuides.request.copy(id = "bear_u9", userUid = "u9"))
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertEquals(listOf(cancelled.id, TestGuides.request.id), vm.state.requests.map { it.id })
        auth.user.value = null
        assertFalse(vm.state.loading)
        assertTrue(vm.state.requests.isEmpty())
    }

    @Test
    fun `a request is cancelled, and a failure says so`() = runTest {
        requests.give(TestGuides.request)
        val vm = viewModel()
        collecting(vm.uiState)
        requests.failure = IllegalStateException("offline")
        vm.cancel(TestGuides.request.id)
        assertTrue(vm.state.failed)
        requests.failure = null
        requests.onWrite = CompletableDeferred()
        vm.cancel(TestGuides.request.id)
        assertTrue(vm.state.busy)
        vm.cancel(TestGuides.request.id) // a second tap waits
        requests.onWrite?.complete(Unit)
        assertFalse(vm.state.failed)
        assertEquals(RequestStatus.CANCELLED, vm.state.requests.single().status)
    }

    @Test
    fun `send again confirms with the contact as it is now, then sends it`() = runTest {
        requests.give(cancelled)
        users.contacts.value = mapOf(uid to UserContact("Jo", "old@example.com", "418 555 0100"))
        val vm = viewModel()
        collecting(vm.uiState)
        vm.sendAgain(cancelled.id)
        assertEquals(cancelled, vm.state.confirm)
        assertEquals(UserContact("Jo", TestGuides.user.email, "418 555 0100"), vm.state.contact)
        vm.dismissConfirm()
        assertNull(vm.state.confirm)
        vm.confirmSend()
        assertEquals("nothing to send", RequestStatus.CANCELLED, requests.requests.value.getValue(cancelled.id).status)
        vm.sendAgain(cancelled.id)
        vm.confirmSend()
        val again = requests.requests.value.getValue(cancelled.id)
        assertEquals(RequestStatus.PENDING, again.status)
        assertEquals("418 555 0100", again.user.phone)
        assertNull(vm.state.confirm)
    }

    @Test
    fun `send again with an incomplete contact opens the contact form once`() = runTest {
        auth.user.value = TestGuides.user.copy(name = "")
        requests.give(cancelled)
        val vm = viewModel()
        collecting(vm.uiState)
        vm.sendAgain(cancelled.id)
        assertTrue(vm.state.askContact)
        assertNull(vm.state.confirm)
        vm.contactAsked()
        assertFalse(vm.state.askContact)
        users.saveContact(uid, UserContact("Jo"))
        assertEquals(cancelled, vm.state.confirm)
    }
}
