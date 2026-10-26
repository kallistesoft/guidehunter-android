package ca.kallistesoft.guidehunter.core.testing

import app.cash.turbine.ReceiveTurbine

/** Skips emissions until one matches, e.g. past a "loading" state whose timing depends on the dispatcher. */
suspend fun <T> ReceiveTurbine<T>.awaitItemMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
