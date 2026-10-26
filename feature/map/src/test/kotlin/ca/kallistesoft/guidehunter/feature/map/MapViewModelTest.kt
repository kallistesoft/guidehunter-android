package ca.kallistesoft.guidehunter.feature.map

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val guides = FakeGuideRepository()
    private val location = FakeLocationRepository()
    private val ads = FakeRewardedAds()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = MapViewModel(guides, location, ads, saved)

    private val MapViewModel.ids get() = uiState.value.guides.map { it.guide.id }
    private val MapViewModel.contact get() = uiState.value.selected?.contact

    @Test
    fun `loading until the guides come, then every guide`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.loading)
        collecting(vm.uiState)
        assertEquals(listOf("bear", "salmon", "moose"), vm.ids)
        assertTrue(vm.uiState.value.guides.all { it.distanceKm == null })
    }

    @Test
    fun `filters narrow the guides, and a species of the other discipline is cleared`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals(listOf("salmon", "moose"), vm.ids)
        vm.chooseSpecies(Species.WALLEYE)
        assertEquals(listOf("moose"), vm.ids)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals("same discipline keeps the species", Species.WALLEYE, vm.uiState.value.filter.species)
        vm.chooseDiscipline(Discipline.HUNTING)
        assertNull(vm.uiState.value.filter.species)
        assertEquals(listOf("bear", "moose"), vm.ids)
        vm.chooseDiscipline(null)
        vm.chooseSpecies(Species.MOOSE)
        assertEquals(listOf("moose"), vm.ids)
    }

    @Test
    fun `the filters survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            chooseDiscipline(Discipline.HUNTING)
            chooseSpecies(Species.BLACK_BEAR)
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(listOf("bear"), restored.ids)
    }

    @Test
    fun `with the user's location, guides show their distance, nearest first`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        location.location = TestGuides.quebecCity
        vm.refreshLocation()
        assertEquals(TestGuides.quebecCity, vm.uiState.value.userLocation)
        assertEquals(listOf("moose", "salmon", "bear"), vm.ids)
        val bear = vm.uiState.value.guides.first { it.guide.id == "bear" }
        assertEquals(TestGuides.bear.location.distanceKm(TestGuides.quebecCity).toInt().toDouble(), bear.distanceKm!!.toDouble(), 1.0)

        location.location = null // permission revoked: the last known place stays
        vm.refreshLocation()
        assertEquals(TestGuides.quebecCity, vm.uiState.value.userLocation)
    }

    @Test
    fun `opening a guide shows its offer with the contact locked, and gets an ad ready`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        assertEquals(TestGuides.bear, vm.uiState.value.selected?.item?.guide)
        assertEquals(ContactState.Locked, vm.contact)
        assertEquals(1, ads.preloads)
        assertTrue("nothing read before the ad", guides.contactsRead.isEmpty())
        vm.dismiss()
        assertNull(vm.uiState.value.selected)
    }

    @Test
    fun `an ad watched to the end shows the contact, and the guide stays unlocked`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
        assertEquals(1, ads.shown)

        vm.dismiss()
        vm.select("bear")
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
        assertEquals("no second ad", 1, ads.shown)
        assertEquals("no preload for an unlocked guide", 1, ads.preloads)
    }

    @Test
    fun `no ad to watch through no fault of the user still unlocks`() = runTest {
        ads.outcome = RewardOutcome.NO_ADS
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("moose")
        vm.unlock()
        assertEquals(ContactState.Unlocked(TestGuides.contacts.getValue("moose")), vm.contact)
    }

    @Test
    fun `an ad closed early or offline leaves the contact hidden`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        ads.outcome = RewardOutcome.NOT_EARNED
        vm.unlock()
        assertEquals(ContactState.Failed(UnlockError.NOT_EARNED), vm.contact)
        ads.outcome = RewardOutcome.OFFLINE
        vm.unlock()
        assertEquals(ContactState.Failed(UnlockError.OFFLINE), vm.contact)
        assertTrue(guides.contactsRead.isEmpty())
        ads.outcome = RewardOutcome.EARNED
        vm.unlock()
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
    }

    @Test
    fun `a guide without contact details says so`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("salmon")
        vm.unlock()
        assertEquals(ContactState.Missing, vm.contact)
    }

    @Test
    fun `a contact that fails to load is tried again without another ad`() = runTest {
        guides.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        assertEquals(ContactState.Failed(UnlockError.LOAD_FAILED), vm.contact)
        guides.failure = null
        vm.unlock()
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
        assertEquals(1, ads.shown)
    }

    @Test
    fun `reopening a guide whose contact failed to load tries again, without an ad`() = runTest {
        guides.failure = IllegalStateException("offline")
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        vm.dismiss()
        guides.failure = null
        vm.select("bear")
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
        assertEquals(1, ads.shown)
    }

    @Test
    fun `a cancelled load isn't reported as a failure`() = runTest {
        guides.failure = CancellationException()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        assertEquals(ContactState.Unlocking, vm.contact)
    }

    @Test
    fun `a second tap while the ad shows does nothing, and no guide open means no ad`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.unlock()
        assertEquals(0, ads.shown)
        vm.select("bear")
        ads.onShow = CompletableDeferred()
        vm.unlock()
        assertEquals(ContactState.Unlocking, vm.contact)
        vm.unlock()
        assertEquals(1, ads.shown)
        ads.onShow?.complete(Unit)
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
    }

    @Test
    fun `the open guide and the unlocked ones survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            select("bear")
            unlock()
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals("bear", restored.uiState.value.selected?.item?.guide?.id)
        assertEquals("reloaded without an ad", ContactState.Unlocked(TestGuides.bearContact), restored.contact)
        assertEquals(1, ads.shown)
    }

    @Test
    fun `a selected guide that's no longer published closes`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        guides.guides.value = listOf(TestGuides.salmon)
        assertNull(vm.uiState.value.selected)
    }
}
