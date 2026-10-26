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
import ca.kallistesoft.guidehunter.feature.becomeguide.BecomeGuideScreen
import ca.kallistesoft.guidehunter.feature.map.MapContent
import ca.kallistesoft.guidehunter.feature.map.MapScreen
import ca.kallistesoft.guidehunter.navigation.BecomeGuide
import ca.kallistesoft.guidehunter.navigation.GuideMap

/** Draws the maps; the default is Google Maps, tests pass stand-ins (Google Maps can't draw on the JVM). */
class Maps(
    val guides: (@Composable (MapContent, Modifier) -> Unit)? = null,
    val picker: (@Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit)? = null,
)

/** App navigation (Navigation 3): the map, and the "Become a guide" form over it. */
@Composable
fun GuideHunterApp(modifier: Modifier = Modifier, maps: Maps = Maps()) {
    val backStack = rememberNavBackStack(GuideMap)
    // Guarded: two quick taps on back (or "Back to the map") during the animation must not empty the stack.
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
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
                // Guarded: two quick taps must not push the form twice (duplicate keys break the saved state).
                val open: () -> Unit = { if (backStack.lastOrNull() != BecomeGuide) backStack.add(BecomeGuide) }
                maps.guides?.let { MapScreen(onBecomeGuide = open, map = it) } ?: MapScreen(onBecomeGuide = open)
            }
            entry<BecomeGuide> {
                maps.picker?.let { BecomeGuideScreen(onBack = back, picker = it) } ?: BecomeGuideScreen(onBack = back)
            }
        },
    )
}
