package ca.kallistesoft.guidehunter.core.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Collects [flows] for the rest of the test, as a screen does. ViewModels share their state `WhileSubscribed` and
 * load in `onStart` (see .claude/skills/viewmodel-state), so a test that reads `uiState.value` must collect it first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.collecting(vararg flows: Flow<*>) {
    for (flow in flows) backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}
