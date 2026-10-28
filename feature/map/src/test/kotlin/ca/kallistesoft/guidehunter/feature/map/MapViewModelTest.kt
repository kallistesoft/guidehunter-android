package ca.kallistesoft.guidehunter.feature.map

import androidx.lifecycle.SavedStateHandle
import ca.kallistesoft.guidehunter.core.ads.RewardOutcome
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.Discipline
import ca.kallistesoft.guidehunter.core.model.GeoBounds
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.core.model.Schedule
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Species
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.TripLength
import ca.kallistesoft.guidehunter.core.testing.FakeGuideRepository
import ca.kallistesoft.guidehunter.core.testing.FakeLocationRepository
import ca.kallistesoft.guidehunter.core.testing.FakeRewardedAds
import ca.kallistesoft.guidehunter.core.testing.MainDispatcherRule
import ca.kallistesoft.guidehunter.core.testing.TestGuides
import ca.kallistesoft.guidehunter.core.testing.collecting
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val guides = FakeGuideRepository()
    private val location = FakeLocationRepository()
    private val ads = FakeRewardedAds()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = MapViewModel(guides, location, ads, saved, { TestGuides.today })

    private val MapViewModel.ids get() = uiState.value.guides.map { it.guide.id }
    private val MapViewModel.contact get() = uiState.value.selected?.contact

    @Test
    fun `loading until the guides come, then every guide`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.loading)
        collecting(vm.uiState)
        assertEquals(listOf("bear", "salmon", "moose"), vm.ids)
        assertTrue(vm.uiState.value.guides.all { it.distanceKm == null })
        assertEquals("the location is read once at start (null without the permission)", 1, location.reads)
    }

    @Test
    fun `the cards keep the guides on screen, the pins every guide`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals("all of them until the map tells", vm.ids, vm.uiState.value.inView.map { it.guide.id })
        // Southern Quebec, west of Gaspé: the bear and the moose.
        vm.showArea(GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -70.0)))
        assertEquals(listOf("bear", "moose"), vm.uiState.value.inView.map { it.guide.id })
        assertEquals(listOf("bear", "salmon", "moose"), vm.ids)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals("the filters still apply", listOf("moose"), vm.uiState.value.inView.map { it.guide.id })
    }

    @Test
    fun `filters narrow the guides, and species of the other discipline are dropped`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals(listOf("salmon", "moose"), vm.ids)
        vm.toggleSpecies(Species.WALLEYE)
        assertEquals(listOf("moose"), vm.ids)
        vm.chooseDiscipline(Discipline.FISHING)
        assertEquals("same discipline keeps the species", setOf(Species.WALLEYE), vm.uiState.value.filter.species)
        vm.chooseDiscipline(Discipline.HUNTING)
        assertEquals(emptySet<Species>(), vm.uiState.value.filter.species)
        assertEquals(listOf("bear", "moose"), vm.ids)
        vm.chooseDiscipline(null)
        vm.toggleSpecies(Species.MOOSE)
        assertEquals(listOf("moose"), vm.ids)
    }

    @Test
    fun `several species show the guides offering any of them`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.toggleSpecies(Species.BLACK_BEAR)
        vm.toggleSpecies(Species.WALLEYE)
        assertEquals(listOf("bear", "moose"), vm.ids)
        vm.toggleSpecies(Species.BLACK_BEAR)
        assertEquals("ticking again removes it", listOf("moose"), vm.ids)
        vm.clearSpecies()
        assertEquals(3, vm.ids.size)
    }

    @Test
    fun `the filters survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            toggleSpecies(Species.BLACK_BEAR)
            toggleSpecies(Species.ATLANTIC_SALMON)
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(setOf(Species.BLACK_BEAR, Species.ATLANTIC_SALMON), restored.uiState.value.filter.species)
        assertEquals(listOf("bear", "salmon"), restored.ids)
    }

    @Test
    fun `with the user's location, guides show their distance, nearest first`() = runTest {
        location.location = TestGuides.quebecCity
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals("read when the screen opens", TestGuides.quebecCity, vm.uiState.value.userLocation)
        assertEquals(listOf("moose", "salmon", "bear"), vm.ids)
        val bear = vm.uiState.value.guides.first { it.guide.id == "bear" }
        assertEquals(TestGuides.bear.location.distanceKm(TestGuides.quebecCity).roundToInt(), bear.distanceKm)

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
        assertEquals("no second read either", listOf("bear"), guides.contactsRead)
        assertEquals("no preload for an unlocked guide", 1, ads.preloads)
    }

    @Test
    fun `reopening a guide whose contact is on its way doesn't read it twice`() = runTest {
        guides.onContact = CompletableDeferred()
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        vm.unlock()
        vm.dismiss()
        vm.select("bear")
        assertEquals(ContactState.Unlocking, vm.contact)
        guides.onContact?.complete(Unit)
        assertEquals(ContactState.Unlocked(TestGuides.bearContact), vm.contact)
        assertEquals(listOf("bear"), guides.contactsRead)
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
    fun `the restored contact is reloaded once, not on every return to the screen`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            select("bear")
            unlock()
        }
        val restored = viewModel(saved)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        assertEquals(listOf("bear", "bear"), guides.contactsRead)
        first.cancel()
        advanceTimeBy(10_000) // sharing stops, and starts again on the next collection
        collecting(restored.uiState)
        assertEquals("not read again", listOf("bear", "bear"), guides.contactsRead)
    }

    @Test
    fun `a restored contact that fails to load says so, still without an ad`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            select("bear")
            unlock()
        }
        guides.failure = IllegalStateException("offline")
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(ContactState.Failed(UnlockError.LOAD_FAILED), restored.contact)
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

    private fun day(month: Int, day: Int) = LocalDate(2027, month, day)

    @Test
    fun `the dates show the guides with a trip that fits them, and those on request`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.chooseDates(Trip(day(5, 14), day(5, 31)))
        assertEquals("bear's week of May 15 fits, salmon's June half day doesn't", listOf("bear", "moose"), vm.ids)
        vm.chooseDates(Trip(day(5, 18), day(5, 31)))
        assertEquals("a week leaving May 15 starts too early", listOf("moose"), vm.ids)
        vm.chooseDates(Trip(day(7, 2)))
        assertEquals("only moose, on request", listOf("moose"), vm.ids)
        vm.chooseDiscipline(Discipline.HUNTING)
        assertEquals("the discipline keeps the dates", Trip(day(7, 2)), vm.uiState.value.filter.dates)
        vm.clearDates()
        assertEquals(listOf("bear", "moose"), vm.ids)
        assertEquals(TestGuides.today, vm.uiState.value.today)
    }

    @Test
    fun `an offer whose dates are over leaves the map`() = runTest {
        val past = TestGuides.bear.copy(schedule = Schedule(TripLength.Days(7), listOf(Departure(day(3, 1)))))
        guides.guides.value = listOf(past, TestGuides.moose)
        val vm = viewModel()
        collecting(vm.uiState)
        assertEquals(listOf("moose"), vm.ids)
    }

    @Test
    fun `an open guide lists its trips, the user picks one, and the dates pick the first that fits`() = runTest {
        val vm = viewModel()
        collecting(vm.uiState)
        vm.select("bear")
        val selected = vm.uiState.value.selected!!
        assertEquals(listOf(day(5, 1), day(5, 15), day(6, 5)), selected.trips.map { it.start })
        assertNull(selected.trip)
        vm.chooseTrip(selected.trips[1])
        assertEquals(Trip(day(5, 15), day(5, 21)), vm.uiState.value.selected!!.trip)
        vm.chooseTrip(selected.trips[1])
        assertNull("again: unchosen", vm.uiState.value.selected!!.trip)
        vm.chooseDates(Trip(day(6, 1), day(6, 30)))
        assertEquals(day(6, 5), vm.uiState.value.selected!!.trip!!.start)
        vm.select("moose")
        assertEquals(emptyList<Trip>(), vm.uiState.value.selected!!.trips)
        assertNull(vm.uiState.value.selected!!.trip)
    }

    @Test
    fun `the dates and the chosen trip survive process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            chooseDates(Trip(day(6, 1), day(6, 30)))
            select("salmon")
            chooseTrip(Trip(day(6, 10), slot = Slot.AFTERNOON))
        }
        val restored = viewModel(saved)
        collecting(restored.uiState)
        assertEquals(Trip(day(6, 1), day(6, 30)), restored.uiState.value.filter.dates)
        assertEquals(Trip(day(6, 10), slot = Slot.AFTERNOON), restored.uiState.value.selected!!.trip)
        restored.select("salmon")
        assertEquals("opening the same guide keeps the trip", Slot.AFTERNOON, restored.uiState.value.selected!!.trip!!.slot)
        restored.dismiss()
        assertNull(restored.uiState.value.selected)
    }
}
