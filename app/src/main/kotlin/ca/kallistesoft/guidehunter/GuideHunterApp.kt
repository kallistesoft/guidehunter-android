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
fun GuideHunterApp(maps: Maps = Maps()) {
    val backStack = rememberNavBackStack(GuideMap)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider<NavKey> {
            entry<GuideMap> {
                val open: () -> Unit = { backStack.add(BecomeGuide) }
                maps.guides?.let { MapScreen(onBecomeGuide = open, map = it) } ?: MapScreen(onBecomeGuide = open)
            }
            entry<BecomeGuide> {
                maps.picker?.let { BecomeGuideScreen(onBack = back, picker = it) } ?: BecomeGuideScreen(onBack = back)
            }
        },
    )
}
