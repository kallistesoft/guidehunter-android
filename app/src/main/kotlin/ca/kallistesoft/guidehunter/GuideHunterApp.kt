package ca.kallistesoft.guidehunter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import ca.kallistesoft.guidehunter.feature.account.ChooseModeScreen
import ca.kallistesoft.guidehunter.feature.account.SettingsScreen
import ca.kallistesoft.guidehunter.feature.account.WelcomeScreen
import ca.kallistesoft.guidehunter.feature.becomeguide.GuideProfileScreen
import ca.kallistesoft.guidehunter.feature.becomeguide.MyOffersScreen
import ca.kallistesoft.guidehunter.feature.becomeguide.OfferEditorScreen
import ca.kallistesoft.guidehunter.feature.map.MapContent
import ca.kallistesoft.guidehunter.feature.map.MapScreen
import ca.kallistesoft.guidehunter.navigation.ChooseMode
import ca.kallistesoft.guidehunter.navigation.GuideMap
import ca.kallistesoft.guidehunter.navigation.GuideProfile
import ca.kallistesoft.guidehunter.navigation.MyOffers
import ca.kallistesoft.guidehunter.navigation.OfferEditor
import ca.kallistesoft.guidehunter.navigation.Settings
import ca.kallistesoft.guidehunter.navigation.Welcome

/** Draws the maps; the default is Google Maps, tests pass stand-ins (Google Maps can't draw on the JVM). */
class Maps(
    val guides: (@Composable (MapContent, Modifier) -> Unit)? = null,
    val picker: (@Composable (GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit)? = null,
)

/**
 * App navigation (Navigation 3). The home ([AppViewModel.home]) is the welcome, the map (user mode) or the guide's
 * offers (guide mode); when it changes (the welcome done, a mode switch, a sign-out, the account deleted) the back
 * stack starts over from it.
 */
@Composable
fun GuideHunterApp(viewModel: AppViewModel, modifier: Modifier = Modifier, maps: Maps = Maps()) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    home?.let { AppNavigation(it, modifier, maps) }
}

@Composable
private fun AppNavigation(home: NavKey, modifier: Modifier, maps: Maps) {
    val backStack = rememberNavBackStack(home)
    LaunchedEffect(home) {
        if (backStack.first() != home) {
            backStack.add(home)
            backStack.retainAll(listOf(home))
        }
    }
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
            entry<Welcome> {
                WelcomeScreen(onSignedIn = { open(ChooseMode) })
            }
            entry<ChooseMode> {
                ChooseModeScreen()
            }
            entry<GuideMap> {
                val settings = { open(Settings) }
                maps.guides?.let { MapScreen(onSettings = settings, map = it) } ?: MapScreen(onSettings = settings)
            }
            entry<MyOffers> {
                MyOffersScreen(
                    onSettings = { open(Settings) },
                    onEditProfile = { open(GuideProfile) },
                    onAddOffer = { open(OfferEditor()) },
                    onEditOffer = { open(OfferEditor(it)) },
                )
            }
            entry<Settings> {
                SettingsScreen(onBack = back, onGuideProfile = { open(GuideProfile) })
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
