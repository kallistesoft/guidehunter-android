package ca.kallistesoft.guidehunter.feature.account

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import ca.kallistesoft.guidehunter.core.model.ContactField
import ca.kallistesoft.guidehunter.core.model.UserContact
import ca.kallistesoft.guidehunter.core.testing.FakeAuthRepository
import ca.kallistesoft.guidehunter.core.testing.FakeUserAccountRepository
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyContactViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = TestGuides.user.uid
    private val auth = FakeAuthRepository(TestGuides.user)
    private val users = FakeUserAccountRepository()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = MyContactViewModel(auth, users, saved)

    private val MyContactViewModel.state get() = uiState.value

    @Test
    fun `a first contact starts with the Google name and email`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.loading)
        collecting(vm.uiState)
        assertEquals(UserContact(TestGuides.user.name, TestGuides.user.email), vm.state.form)
    }

    @Test
    fun `a saved contact is read back, always with the account's email`() = runTest {
        users.contacts.value = mapOf(uid to TestGuides.userContact.copy(email = "old@example.com"))
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(TestGuides.userContact, vm.state.form)
    }

    @Test
    fun `what's missing shows on save, then follows the typing, a valid contact is saved`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setName(" ")
        assertTrue("not before a first try", vm.state.errors.isEmpty())
        vm.setPhone("555")
        vm.submit()
        assertEquals(setOf(ContactField.NAME, ContactField.PHONE), vm.state.errors)
        vm.setName("Alex")
        assertEquals(setOf(ContactField.PHONE), vm.state.errors)
        vm.setPhone("418 555 0199 ")
        vm.submit()
        assertEquals(SaveState.SAVED, vm.state.save)
        assertEquals(UserContact("Alex", TestGuides.user.email, "418 555 0199"), users.contacts.value[uid])
        vm.submit()
        assertEquals("once", SaveState.SAVED, vm.state.save)
    }

    @Test
    fun `a failed save says so, and typing clears it`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        users.failure = IllegalStateException("offline")
        vm.submit()
        assertEquals(SaveState.FAILED, vm.state.save)
        vm.setName("Alex")
        assertEquals(SaveState.EDITING, vm.state.save)
        users.failure = BackendNotConfiguredException()
        vm.submit()
        assertEquals(SaveState.NOT_CONFIGURED, vm.state.save)
    }

    @Test
    fun `a second tap while saving does nothing`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        users.onWrite = CompletableDeferred()
        vm.submit()
        vm.submit()
        assertEquals(SaveState.SAVING, vm.state.save)
        users.onWrite?.complete(Unit)
        assertEquals(SaveState.SAVED, vm.state.save)
    }

    @Test
    fun `signed out, nothing is saved`() = runTest {
        auth.user.value = null
        val vm = viewModel()
        collecting(vm.uiState)
        vm.setName("Alex")
        vm.submit()
        assertEquals(SaveState.FAILED, vm.state.save)
    }

    @Test
    fun `what was typed survives process death, errors too, and is read once`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            collecting(uiState)
            setName("")
            submit()
            setPhone("418 555 0100")
        }
        users.contacts.value = mapOf(uid to TestGuides.userContact)
        val restored = viewModel(saved)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        assertEquals(UserContact("", TestGuides.user.email, "418 555 0100"), restored.state.form)
        assertEquals(setOf(ContactField.NAME), restored.state.errors)
        restored.setName("Jo")
        first.cancel()
        advanceTimeBy(10_000)
        collecting(restored.uiState)
        assertEquals("not read again", "Jo", restored.state.form.name)
    }
}
