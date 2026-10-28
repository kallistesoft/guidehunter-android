package ca.kallistesoft.guidehunter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.feature.becomeguide.GuideProfileScreen
import ca.kallistesoft.guidehunter.feature.becomeguide.MyOffersScreen
import ca.kallistesoft.guidehunter.feature.becomeguide.OfferEditorScreen
import ca.kallistesoft.guidehunter.feature.map.MapContent
import ca.kallistesoft.guidehunter.feature.map.MapScreen
import ca.kallistesoft.guidehunter.navigation.GuideMap
import ca.kallistesoft.guidehunter.navigation.GuideProfile
import ca.kallistesoft.guidehunter.navigation.MyOffers
import ca.kallistesoft.guidehunter.navigation.OfferEditor

/** Draws the maps; the default is Google Maps, tests pass stand-ins (Google Maps can't draw on the JVM). */
class Maps(
    val guides: (@Composable (MapContent, Modifier) -> Unit)? = null,
    val picker: (@Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit)? = null,
)

/** App navigation (Navigation 3): the map, and over it the guide's offers, profile and offer form. */
@Composable
fun GuideHunterApp(modifier: Modifier = Modifier, maps: Maps = Maps()) {
    val backStack = rememberNavBackStack(GuideMap)
    // Guarded: two quick taps on back (or "Back to the map") during the animation must not empty the stack.
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    // Guarded: two quick taps must not push a screen twice (duplicate keys break the saved state).
    val open: (NavKey) -> Unit = { key -> if (backStack.lastOrNull() != key) backStack.add(key) }
    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider<NavKey> {
            entry<GuideMap> {
                val becomeGuide = { open(MyOffers) }
                maps.guides?.let { MapScreen(onBecomeGuide = becomeGuide, map = it) } ?: MapScreen(onBecomeGuide = becomeGuide)
            }
            entry<MyOffers> {
                MyOffersScreen(
                    onBack = back,
                    onEditProfile = { open(GuideProfile) },
                    onAddOffer = { open(OfferEditor()) },
                    onEditOffer = { open(OfferEditor(it)) },
                )
            }
            entry<GuideProfile> {
                GuideProfileScreen(onBack = back, onDone = back)
            }
            entry<OfferEditor> { key ->
                val editProfile = { open(GuideProfile) }
                maps.picker?.let {
                    OfferEditorScreen(key.offerId, onBack = back, onDone = back, onEditProfile = editProfile, picker = it)
                } ?: OfferEditorScreen(key.offerId, onBack = back, onDone = back, onEditProfile = editProfile)
            }
        },
    )
}
