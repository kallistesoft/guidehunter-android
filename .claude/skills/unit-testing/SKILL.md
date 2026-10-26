---
name: unit-testing
description: How to write and run GuideHunter's unit tests (JVM + Robolectric, no device) and keep Kover coverage at 90% of lines for every class. Use when adding or changing any code (a ViewModel, a screen, a repository, a map), writing or fixing a test, when `koverVerifyUnit` or the CI "Coverage" step fails, or when asked to raise coverage. For tests on a device or emulator, see .claude/skills/testing-on-emulator.
---

# Unit tests and coverage in GuideHunter

**Rule: every class keeps at least 90% of its lines covered by the unit tests.** `./gradlew :koverVerifyUnit`
(CI's "Coverage" step) fails otherwise. New code comes with its tests in the same change.

Kover (`build-logic/.../KoverConventionPlugin.kt`) measures each module's `unit` variant (debug unit tests, or the
JVM tests of `:core:model`) and merges them at the root, so a class may be covered by another module's tests. Left
out: generated code (Hilt, Dagger, `ComposableSingletons`, `BuildConfig`) and `@Preview` functions. `:core:testing`
isn't measured.

## Commands

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME=~/Library/Android/sdk
./gradlew :feature:map:testDebugUnitTest --tests '*MapScreenTest*'    # one module / one class while working
./gradlew testDebugUnitTest :core:model:test                          # every unit test
./gradlew :koverVerifyUnit                                            # the coverage rule (runs the tests it needs)
./gradlew :koverHtmlReportUnit     # build/reports/kover/htmlUnit/index.html: the missed lines, in red
./gradlew :koverXmlReportUnit      # build/reports/kover/reportUnit.xml, for scripts
```

Use `:koverVerifyUnit` (with the colon, the merged report). Without it, Gradle also runs each module's own task,
which has no rule but costs time.

## Reading a failure

```
lines covered percentage for class 'ca.kallistesoft.guidehunter.feature.map.MapViewModel$select$1' is 0.0 …
```

Kover counts **every JVM class**, including the ones Kotlin generates inside yours:

| Class in the report | What it is | How to cover it |
|---|---|---|
| `FooKt` | top-level functions of `Foo.kt` (composables) | render them with every state they branch on |
| `Foo$bar$1` | a `launch { }` / suspend lambda in `bar()` | call `bar()` and let the coroutine run |
| `FooKt$Content$2$1$1$3$1$1` | a click lambda inside a composable | `performClick()` on that button and assert the callback |
| `Foo$Companion` | companion with a non-private `val` | make an unused `val` `private` |
| `Foo$DefaultImpls` | default method of an interface | call the default |

Open the HTML report on that class to see the exact lines.

## Where a test goes, and what it uses

| Code | Test | Tools |
|---|---|---|
| `core/model` rules | plain JUnit, `core/model/src/test` | plain values (the module has no test dependencies but JUnit) |
| ViewModel | JVM test, `FooViewModelTest` | `MainDispatcherRule`, `runTest`, Turbine or `collecting()`, fakes from `:core:testing` |
| Screen / composable | Robolectric Compose test, `FooScreenTest` | `createComposeRule()`, `GuideHunterTheme`, real ViewModel + fakes, a stand-in map |
| Google Maps composable | Robolectric, map view shadowed | `GuideMapTest`: a `ShadowMapView` hands over a mocked `GoogleMap`; MockK for `CameraUpdateFactory`, `BitmapDescriptorFactory` |
| Firestore repository | JVM test on mocked Firestore | MockK, `Tasks.forResult` (`FirestoreRepositoriesTest`); the document mapping is plain maps (`FirestoreDocumentsTest`) |
| AdMob / UMP | through the class's seam | fake `AdsSdk` and `ConsentGateway` (`AdsManagerTest`), MockK for the SDKs (`GoogleAdsTest`) |
| App navigation, Activity | Robolectric in `app/src/test` | `createAndroidComposeRule<MainActivity>()`: the real Hilt graph with the sample guides |

Shared doubles live in `:core:testing`: `FakeGuideRepository`, `FakeGuideApplicationRepository`,
`FakeLocationRepository`, `FakeRewardedAds`, `FakeAdConsent`, `TestGuides`, `MainDispatcherRule`, `collecting()`.
Add to them rather than writing a second fake in a module.

### A ViewModel test

The state only loads while collected (.claude/skills/viewmodel-state), so collect it first:

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()

private val guides = FakeGuideRepository()
private val ads = FakeRewardedAds()

private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
    MapViewModel(guides, FakeLocationRepository(), ads, saved)

@Test
fun `an ad closed early leaves the contact hidden`() = runTest {
    val vm = viewModel()
    collecting(vm.uiState)
    vm.select("bear")
    ads.outcome = RewardOutcome.NOT_EARNED
    vm.unlock()
    assertEquals(ContactState.Failed(UnlockError.NOT_EARNED), vm.uiState.value.selected?.contact)
    assertTrue(guides.contactsRead.isEmpty())                   // what the fake repository received
}
```

Something held on screen (an ad showing, a form sending) uses the fake's `CompletableDeferred` (`ads.onShow`,
`repository.onSubmit`), so the test checks the state in between and a second tap. Process death is a second
ViewModel on the same `SavedStateHandle`.

### A screen test

Drive the real screen with a real ViewModel built on fakes, click every action and assert what happened, not only
that something rendered. Screens take their Google map as a parameter (Google Maps can't draw on the JVM), and
tests pass a plain composable in its place.

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
class MapScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `become a guide opens the form`() {
        var opened = 0
        compose.setContent {
            GuideHunterTheme {
                MapScreen(onBecomeGuide = { opened++ }, viewModel = MapViewModel(/* fakes */), map = { _, _ -> })
            }
        }
        compose.onNodeWithTag("become-guide").performClick()
        assertEquals(1, opened)
    }
}
```

- Find nodes by **test tag** first, then by the app's own strings (`app.getString(R.string.no_guides)`), never by
  a hard-coded English text, except where the test is about the English text itself.
- Content taller than the screen: `performScrollTo()` (or `performScrollToNode` in a lazy list).
- Each branch of the `UiState` (loading, empty, error, done) is a state to render once.
- Links (dialer, mail, website) are checked with `shadowOf(app).nextStartedActivity`.

## Rules

- **Test behavior.** Each test name says what the user or the code gets (`` `an ad closed early leaves the contact
  hidden` ``); each test asserts a result: a callback, a saved value, a shown text. A test that only executes lines
  doesn't count.
- **No Kover exclusions for hand-written code, and never lower the threshold.** If a line truly can't run on the JVM,
  add a seam (a parameter with the real thing as default, like `MapScreen(map = …)`, or a Robolectric shadow like
  `GuideMapTest`), keep it minimal, and say why in a comment.
- **Dead code is deleted**, not tested: an unreachable branch or getter that drags a class under 90% is a finding.
- Fakes over mocks. Use MockK only for third-party classes without a seam (Firebase, AdMob, Google Maps).
- No wall clock or real time in assertions: use `runTest` virtual time.

## Checklist

```
Unit tests:
- [ ] New or changed code has tests in the same change (ViewModel test and/or screen test)
- [ ] Every new click lambda, launch { } and UiState branch runs in a test
- [ ] Tests assert results and are named for the behavior
- [ ] ./gradlew testDebugUnitTest :core:model:test passes
- [ ] ./gradlew :koverVerifyUnit passes (no class under 90%)
- [ ] No new Kover exclusion except for generated code
- [ ] scripts/check-conventions.sh prints "Conventions OK."
```
