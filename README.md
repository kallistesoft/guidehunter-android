# GuideHunter (Android)

Find a hunting or fishing guide anywhere, like Airbnb for hunting and fishing trips. For people with nobody to show
them the way: someone from the city, a new hunter in the family, or a visitor from abroad (a hunter from France who
wants a black bear hunt in Quebec).

**Working name and package** (`ca.kallistesoft.guidehunter`): rename before the first Play upload if the name
changes. Native Android first; iOS later (see FOLLOW-UP.md).

## The MVP

- **No accounts.** Nobody signs in.
- **Map of guides** (Google Maps): a pin per guide (orange for hunting, green for fishing, yellow for both), filters
  (hunting, fishing, species), and how far each guide is once the user shares their location.
- **A guide's offer**: what, where, species, price, description. The guide's **name and contact are hidden until
  the user watches a rewarded ad**. Then they get the phone, email and website, each one tap away.
- **Become a guide**: a form (name, description, hunting/fishing/both, species, town or region, a pin on the map,
  price, contact). It is **emailed to us** for review. Approved guides are put on the map from the Firebase console.

## Architecture

Same stack as BetterHunter: Kotlin, Jetpack Compose (Material 3), Hilt, Navigation 3, coroutines and Flow, Gradle
convention plugins (`build-logic/`) and a version catalog (`gradle/libs.versions.toml`).

| Module | What's in it |
|---|---|
| `:app` | `MainActivity`, navigation (`GuideHunterApp`, `navigation/NavKeys.kt`), build-type settings (AdMob, Maps key, application email) |
| `:core:model` | Pure Kotlin, ready for Kotlin Multiplatform: `Guide`, `GuideContact`, `Species`, `GuideActivity`, `GuideFilter`, `GuideApplication` (and its validation), `GeoPoint.distanceKm` |
| `:core:data` | `GuideRepository` and `GuideApplicationRepository` on Cloud Firestore, `LocationRepository` (Play services), sample guides when Firebase isn't set up |
| `:core:ads` | The rewarded ad and the consent form (AdMob, UMP), from BetterHunter |
| `:core:designsystem` | Theme (BetterHunter's palette and Barlow fonts for now), spacing, sizes, buttons |
| `:core:ui` | Species and activity labels (EN/FR), links (dialer, mail, website) |
| `:core:testing` | Fakes and test rules |
| `:feature:map` | The map, filters, guide cards, the guide's sheet and the unlock |
| `:feature:becomeguide` | The "Become a guide" form |

### Firestore

| Collection | What | Who reads / writes |
|---|---|---|
| `guides/{id}` | The public offer: `title`, `description`, `activity` (`HUNT`, `FISH`, `BOTH`), `species` (names from `Species`), `region`, `lat`, `lng`, `priceFrom` (optional), `currency` (optional, `CAD`) | Everyone reads; you write it in the console |
| `guideContacts/{id}` | Same id: `name`, `phone`, `email`, `website` | Read one at a time after the ad; you write it |
| `guideApplications/{id}` | The forms as sent | The app only creates them |
| `mail/{id}` | The email of each form, sent by the Trigger Email extension | The app only creates them, to your address only |

The rules are in `firestore.rules`. **To publish a guide**: copy the application's fields into a new `guides`
document and its contact into a `guideContacts` document with the same id. It shows on the map at once.

## Setup

None of this is needed to build and run the app: without it, the app shows three sample guides, the maps are grey
and the form can't be sent. Settings that aren't committed go in `local.properties` (or environment variables in
CI).

### Signing certificates (SHA-1)

Firebase and the Maps key both ask for the SHA-1 of the key that signs the app. `./gradlew :app:signingReport`
prints it for each build type (debug: the machine's `~/.android/debug.keystore`; release: your keystore). The debug
key differs from one machine to another: a build from another machine (or a reset cloud container) has a new SHA-1,
which must be added to the Maps key and the Firebase app, and can't update the installed app (uninstall first).

### Firebase

1. At console.firebase.google.com, create a project (`GuideHunter`; Google Analytics isn't needed). Add two Android
   apps: `ca.kallistesoft.guidehunter.debug` (with the debug SHA-1) and `ca.kallistesoft.guidehunter` (the release
   one, when you have it). Download `google-services.json` after adding both, into `app/` (git-ignored; CI writes it
   from the `GOOGLE_SERVICES_JSON` secret).
2. **Firestore Database → Create database**, in production mode, in a region near the users (Montréal is
   `northamerica-northeast1`). Then deploy `firestore.rules`: `firebase deploy --only firestore:rules`, or paste the
   file in the console's **Rules** tab and **Publish**.
3. Check it with one guide, added by hand: a document in `guides` with `title` (string), `activity` (string: `HUNT`,
   `FISH` or `BOTH`), `species` (array of names from `core/model` `Species`, e.g. `BLACK_BEAR`), `region` (string),
   `lat` and `lng` (numbers), `description` (string) and optionally `priceFrom` (number). Its contact goes in
   `guideContacts`, in a document with **the same id**: `name`, `phone`, `email`, `website` (strings). A guide missing
   a required field doesn't show (`core/data` `FirestoreDocuments.kt`).
4. Upgrade to the **Blaze** plan (extensions need it; at this volume it costs about nothing), then install the
   **Trigger Email from Firestore** extension: collection `mail`, and the SMTP connection URI of a dedicated mailbox
   or SendGrid rather than a personal one (anyone can fill it, see FOLLOW-UP.md). For Gmail with an app password:
   `smtps://you%40gmail.com:APP-PASSWORD@smtp.gmail.com:465`.
5. The emails go to `contact@kallistesoft.ca`. To change it, set `APPLICATIONS_EMAIL` in `local.properties` (CI:
   the `APPLICATIONS_EMAIL` variable) **and** change the address in `firestore.rules`.

### Google Maps

In console.cloud.google.com, in the Firebase project: **APIs & Services → Library → Maps SDK for Android → Enable**,
then **Credentials → Create credentials → API key**. Restrict it:
- Application restrictions: **Android apps**, one entry per package name and SHA-1 (debug, and release later);
- API restrictions: **Maps SDK for Android** only.

Put it in `local.properties` as `MAPS_API_KEY=AIza…` (CI: the `MAPS_API_KEY` secret). Without it the maps stay
grey; with a key whose restriction doesn't match the build's SHA-1, they stay grey too (logcat says so).

### AdMob

Debug builds always use Google's test ads. For release, create the AdMob app and a rewarded ad unit, then set
`ADMOB_APP_ID` and `ADMOB_REWARDED_ID` (`local.properties`, or CI variables). Until then, release builds show test
ads too.

### Baseline profile

The release build ships a baseline profile (startup, the map, a guide's offer precompiled). To record it again
after the screens change: `./gradlew :app:generateBaselineProfile` runs `:baselineprofile` on a Gradle-managed
emulator (Pixel 6, API 34, downloaded on first run) and writes `app/src/main/generated/baselineProfiles/`, which is
committed. The test tags it drives are `guide-count`, `guide-sample-bear`, `guide-detail`, `become-guide` and
`field-business-name`: they must stay testTags **and** resource ids (`Modifier.testTag` with
`testTagsAsResourceId` on the root; see `MainActivity`).

## Commands

```sh
./gradlew :app:assembleDebug                      # build
./gradlew testDebugUnitTest :core:model:test      # unit tests (JVM + Robolectric)
./gradlew :koverVerifyUnit                        # coverage: 90% of lines for every class
./gradlew :app:lintDebug
scripts/check-conventions.sh
```

## Conventions

Checked by `scripts/check-conventions.sh` (CI runs it):

1. No build-type branches in code (`BuildConfig.DEBUG`): debug code goes in `src/debug`, release code in
   `src/release`.
2. No dp literals outside `core/designsystem/.../theme/`: use `Spacing`, `Sizes` or `MaterialTheme.shapes`.
3. No `init` block in a ViewModel (`.claude/skills/viewmodel-state`).

Every string is in English and French. Every class keeps 90% of its lines covered by unit tests
(`.claude/skills/unit-testing`).
