---
name: viewmodel-state
description: How GuideHunter ViewModels build their UI state - never an init block; state derived with combine/map + stateIn, or loaded in _uiState.asStateFlow().onStart { } + stateIn. Use when writing, changing, reviewing or testing any ViewModel (a new screen, a new field in a UiState, loading guides or a form, saved state, a ViewModel test), or when scripts/check-conventions.sh reports "no init in a ViewModel".
---

# ViewModel state in GuideHunter

**Rule: a ViewModel has no `init { }` block.** Nothing starts when the ViewModel is created; its state starts
when the screen collects `uiState`, and is shared `WhileSubscribed(5_000)`. `scripts/check-conventions.sh` (run by
CI on every push) fails on an `init` in any class extending `ViewModel()`.

**Why:** work in `init` runs even if no screen ever shows (previews, tests, a ViewModel created and discarded by
navigation), races with the first collection, can't be restarted, and hides the data flow in side effects. A state
derived from flows is always current (a guide published in the console shows at once) and is tested by collecting
it, like the screen does.

## Choose the pattern

```
Is the state a function of repository flows (guides) + the user's in-screen choices (filters, a form)?
├─ yes → Pattern A: combine (or map) + stateIn                    MapViewModel, BecomeGuideViewModel
└─ no, a one-time setup has to happen (draw something random, restore a run) and actions change the state
   imperatively afterwards → Pattern B: _uiState.asStateFlow().onStart { } + stateIn       (none yet)
```

Prefer A. Use B only for state that isn't a pure function of other flows.

### Pattern A: derive with combine

```kotlin
private val filter = MutableStateFlow(GuideFilter(/* restored from savedState */))   // the user's choices

val uiState: StateFlow<MapUiState> = combine(guideRepository.guides, filter, userLocation, selectedId, contacts) {
        guides, filter, location, selectedId, contacts ->
    MapUiState(loading = false, guides = guides.filter(filter::matches) /* … */)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())   // loading = true

fun chooseSpecies(species: Species?) = filter.update { it.copy(species = species) }
```

- In-screen choices live in their own small `MutableStateFlow` (mirrored to `SavedStateHandle` when they must
  survive process death), never copied into the UI state by hand.
- Writes go to the repository (`viewModelScope.launch { repository.submit(…) }`); results that aren't in a
  repository (a contact unlocked, a form sent) go in their own small `MutableStateFlow`, combined like the rest.
- Something to do when the screen starts (reload what process death lost) goes in `.onStart { }` before `stateIn`,
  guarded so it runs once (`MapViewModel.reloadRestoredContact`).
- The `stateIn` initial value is the loading state (`loading = true` or a `Loading` object).

### Pattern B: load once in onStart

```kotlin
private val _uiState = MutableStateFlow<FooUiState>(FooUiState.Loading)

/** Set up when the screen starts collecting, not when the ViewModel is created. */
val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()
    .onStart { if (_uiState.value == FooUiState.Loading) load() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FooUiState.Loading)

private suspend fun load() {
    val restored = restore()
    if (restored != null) show(restored) else start()
}
```

- **Guard on the state, not a flag:** `onStart` runs again each time sharing restarts (the screen comes back after
  5 s). Loading only while the state is still the loading state means the setup never runs twice, and a load
  cancelled midway (screen left during loading) simply starts again.
- `load()` is a private `suspend fun`; it doesn't `launch`.
- Actions read and update `_uiState` (the source of truth), not `uiState`.

## Never

- `init { }` in a ViewModel, including `init { viewModelScope.launch { … } }`.
- A property initializer that starts work: `private val job = viewModelScope.launch { … }`.
- `SharingStarted.Eagerly` to force loading at creation (same problem as `init`). Use `WhileSubscribed(5_000)`.
- Collecting a flow inside the ViewModel to copy it into a `MutableStateFlow` (`guides.collect { _ui.value = … }`):
  use Pattern A.
- Exposing a `MutableStateFlow`; expose `StateFlow` only.

## Tests

The state only loads while collected, as on screen. In a ViewModel test, either:

- collect it with Turbine: `vm.uiState.test { val s = awaitItemMatching { !it.loading } … }`, or
- keep reading `.value` after `collecting(vm.uiState)` (`core:testing`), which collects for the rest of the test.

A test that reads `uiState.value` without collecting sees only the loading state: that's the rule working, not a
bug. With Pattern B, add a test that the setup happens once: collect, stop, `advanceTimeBy(10_000)`, collect
again, same state.

## Checklist

```
ViewModel state:
- [ ] No init block, no work started in a property initializer
- [ ] uiState is derived (combine/map + stateIn) or loaded in onStart with a state guard (+ stateIn)
- [ ] SharingStarted.WhileSubscribed(5_000), initial value = the loading state
- [ ] In-screen choices in their own MutableStateFlow (+ SavedStateHandle if they must survive process death)
- [ ] Tests collect the state (Turbine or collecting()); Pattern B has a "loads once" test
- [ ] scripts/check-conventions.sh prints "Conventions OK."
```
