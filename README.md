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

### Firebase

1. Create a Firebase project and add the Android apps `ca.kallistesoft.guidehunter` and
   `ca.kallistesoft.guidehunter.debug`. Download `google-services.json` into `app/` (git-ignored; CI writes it from
   the `GOOGLE_SERVICES_JSON` secret).
2. Create the Firestore database, then deploy `firestore.rules` (`firebase deploy --only firestore:rules`, or paste
   them in the console).
3. Install the **Trigger Email from Firestore** extension (needs the Blaze plan), with collection `mail` and your
   SMTP account (a Gmail app password or SendGrid).
4. The emails go to `contact@kallistesoft.ca`. To change it, set `APPLICATIONS_EMAIL` in `local.properties` (CI:
   the `APPLICATIONS_EMAIL` variable) **and** change the address in `firestore.rules`.

### Google Maps

Create a key for the Maps SDK for Android in Google Cloud (the Firebase project's), restricted to the two package
names and your signing certificates. Put it in `local.properties` as `MAPS_API_KEY=…` (CI: the `MAPS_API_KEY`
secret).

### AdMob

Debug builds always use Google's test ads. For release, create the AdMob app and a rewarded ad unit, then set
`ADMOB_APP_ID` and `ADMOB_REWARDED_ID` (`local.properties`, or CI variables). Until then, release builds show test
ads too.

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
