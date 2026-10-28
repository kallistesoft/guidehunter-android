# GuideHunter (Android)

Find a hunting or fishing guide anywhere, like Airbnb for hunting and fishing trips. For people with nobody to show
them the way: someone from the city, a new hunter in the family, or a visitor from abroad (a hunter from France who
wants a black bear hunt in Quebec).

**Working name and package** (`ca.kallistesoft.guidehunter`): rename before the first Play upload if the name
changes. Native Android first; iOS later (see FOLLOW-UP.md).

## The MVP

- **One account, two modes.** The first launch asks to sign in with Google (or "Later": the map works signed out),
  then "What brings you here?": **user** (the map) or **guide** (their offers). The app opens on that mode's home;
  Settings switches mode (guide mode needs an account), signs in or out and deletes the account. The mode is kept on
  the phone (DataStore); signing out goes back to user mode, deleting the account back to the welcome.
- **Map of guides** (Google Maps): a pin per guide (orange for hunting, green for fishing, yellow for both), filters
  (hunting, fishing, several species on their own screen, also in Settings and kept for next time, the dates the
  user can go: guides with a trip that fits them), and how far each guide is once the user shares their location.
  Offers whose dates are all over leave the map.
- **A guide's offer**: what, where, species, price, description, how long a trip lasts and its departures (the next
  one shown, the others under "Other dates"; the one the user picks goes into the email to the guide). The guide's
  **name and contact are hidden until the user watches a rewarded ad**. Then they get the phone, email and website,
  each one tap away.
- **Guide mode**: a signed-in guide fills in a **profile** (business name, email, phone, website,
  shared by all their offers) and adds **offers**, as many as they like, in five steps: the trip (title,
  hunting/fishing/both, description), species, where (town or region, a pin on the map), when (half day to weeks,
  departure dates, weekly in one go, these dates only or every year) and price and send (a summary with Change on each
  step). Each new offer and each edit is **emailed to us** for review; `scripts/approve-offer.py` puts it on the map.
  An edit leaves the reviewed version on the map until it's approved. Guides can delete an offer (it leaves the map)
  or their whole account.

## Architecture

Same stack as BetterHunter: Kotlin, Jetpack Compose (Material 3), Hilt, Navigation 3, coroutines and Flow, Gradle
convention plugins (`build-logic/`) and a version catalog (`gradle/libs.versions.toml`).

| Module | What's in it |
|---|---|
| `:app` | `MainActivity`, navigation (`GuideHunterApp`, `navigation/NavKeys.kt`; `AppViewModel` picks the home: welcome, map or guide's offers), build-type settings (AdMob, Maps key, application email) |
| `:core:model` | Pure Kotlin, ready for Kotlin Multiplatform: `Guide`, `GuideContact`, `Species`, `GuideActivity`, `GuideFilter`, `GuideProfile` and `OfferDraft` (and their validation), `GuideOffer`, `AppSettings` (`AppMode`), `GeoPoint.distanceKm` |
| `:core:data` | `GuideRepository` and `GuideAccountRepository` on Cloud Firestore, `AuthRepository` on Firebase Auth, `LocationRepository` (Play services), `SettingsRepository` (onboarding done, mode, the species the map shows; Preferences DataStore), sample guides when Firebase isn't set up |
| `:core:auth` | Google sign-in (Credential Manager) and `AccountSession` (sign in, sign out, delete the account), shared by the welcome and the settings |
| `:core:ads` | The rewarded ad and the consent form (AdMob, UMP), from BetterHunter |
| `:core:designsystem` | Theme (BetterHunter's palette and Barlow fonts for now), spacing, sizes, buttons |
| `:core:ui` | Species and activity labels (EN/FR), links (dialer, mail, website) |
| `:core:testing` | Fakes and test rules |
| `:feature:map` | The map, filters, guide cards, the guide's sheet and the unlock |
| `:feature:account` | The welcome (sign in or later), user or guide, the settings of both modes, and the species the map shows |
| `:feature:becomeguide` | Guide mode: their offers (the guide's home), profile and offer form |

### Firestore

| Collection | What | Who reads / writes |
|---|---|---|
| `guides/{id}` | The public offer: `title`, `description`, `activity` (`HUNT`, `FISH`, `BOTH`), `species` (names from `Species`), `region`, `lat`, `lng`, `priceFrom` (optional), `currency` (optional, `CAD`), `tripLength` (`HALF_DAY` or days), `departures` (ISO dates, `2027-05-20` or `2027-05-20/AM`, `/PM` on a half day; none: dates on request), `everyYear` (bool), `ownerUid` | Everyone reads; written by approval; its guide can delete it |
| `guideContacts/{id}` | Same id: `name`, `phone`, `email`, `website`, `ownerUid` | Read one at a time after the ad; written by approval; its guide's profile updates it |
| `guideProfiles/{uid}` | A guide's `businessName`, `email`, `phone`, `website`, by Firebase user id | Only that guide |
| `guideOffers/{id}` | A guide's offers as written: the offer's fields, `ownerUid`, `status` (`PENDING`, `PUBLISHED`), `live` (a reviewed version is on the map), `updatedAt` | Only their guide; the app always writes `PENDING` and can't change `live` |
| `mail/{id}` | The email of each new or edited offer, sent by the Trigger Email extension | Signed-in guides only create them, to your address only |

The rules are in `firestore.rules`. **To publish an offer** (new or edited), run the command its email gives:
`scripts/approve-offer.py <project> <offerId>` copies it into `guides` and the guide's profile into `guideContacts`
(same id), and marks it published; it shows on the map at once. `scripts/approve-offer.py <project> --list` lists the
offers waiting. To refuse one, reply to its email (it goes to the guide).

## Setup

None of this is needed to build and run the app: without it, the app shows three sample guides, the maps are grey
and guides can't sign in. Settings that aren't committed go in `local.properties` (or environment variables in
CI).

### Signing certificates (SHA-1)

Firebase and the Maps key both ask for the SHA-1 of the key that signs the app. `./gradlew :app:signingReport`
prints it for each build type (debug: the machine's `~/.android/debug.keystore`; release: your keystore). The debug
key differs from one machine to another: a build from another machine (or a reset cloud container) has a new SHA-1,
which must be added to the Maps key and the Firebase app, and can't update the installed app (uninstall first).

### Firebase

Two Firebase projects, so testing never touches real guides:

| Build | Firebase project | Config file (git-ignored) | CI secret |
|---|---|---|---|
| debug (`ca.kallistesoft.guidehunter.debug`) | `guidehunter-dev`, mock guides | `app/src/debug/google-services.json` | `GOOGLE_SERVICES_JSON_DEBUG` |
| release (`ca.kallistesoft.guidehunter`) | `guidehunter-55e70`, production | `app/src/release/google-services.json` | `GOOGLE_SERVICES_JSON_RELEASE` |

Both are in contact@kallistesoft.ca's console. With the Firebase CLI logged in (`firebase login`), a fresh clone gets
its config with `firebase apps:sdkconfig ANDROID <app id> --project <project> -o <config file>`
(`firebase apps:list --project <project>` gives the app id). The debug app needs each machine's debug SHA-1
(`firebase apps:android:sha:create <app id> <sha1> --project guidehunter-dev`), the release app the release key's.

1. Each project has **Authentication** with the **Google** provider on (Authentication → Sign-in method), which
   created the OAuth web client that `google-services.json` carries (`default_web_client_id`). Sign-in needs the
   app's SHA-1 in its Firebase app (the release key's for Play, and Play App Signing's key once uploaded).
2. Each project has a Firestore database in production mode in Montréal (`northamerica-northeast1`). Deploy
   `firestore.rules` to both after a change: `firebase deploy --only firestore:rules` (production, the default in
   `.firebaserc`) and `firebase deploy --only firestore:rules --project guidehunter-dev`.
3. **Mock guides** for the dev project are in `scripts/dev-guides.json`: `scripts/seed-dev-guides.py` writes them
   (again, after editing the file), `--delete` removes them. The script only ever writes to `guidehunter-dev`.
4. A guide is a document in `guides` with `title` (string), `activity` (string: `HUNT`, `FISH` or `BOTH`), `species`
   (array of names from `core/model` `Species`, e.g. `BLACK_BEAR`), `region` (string), `lat` and `lng` (numbers),
   `description` (string) and optionally `priceFrom` (number), `tripLength`, `departures` and `everyYear` (as in `guides`). Its contact goes in `guideContacts`, in a document with
   **the same id**: `name`, `phone`, `email`, `website` (strings). A guide missing a required field doesn't show
   (`core/data` `FirestoreDocuments.kt`).
5. Upgrade to the **Blaze** plan (extensions need it; at this volume it costs about nothing), then install the
   **Trigger Email from Firestore** extension, in each project: collection `mail`, and the SMTP connection URI of a
   dedicated mailbox or SendGrid rather than a personal one (any signed-in guide can fill it, see FOLLOW-UP.md). For Gmail with an app password:
   `smtps://you%40gmail.com:APP-PASSWORD@smtp.gmail.com:465`.
6. The emails go to `contact@kallistesoft.ca`. To change it, set `APPLICATIONS_EMAIL` in `local.properties` (CI:
   the `APPLICATIONS_EMAIL` variable) **and** change the address in `firestore.rules`.

### Google Maps

One key, "GuideHunter Maps (Android)" in the production project (`guidehunter-55e70`), serves both builds. To make
one: in console.cloud.google.com, in the Firebase project, **APIs & Services → Library → Maps SDK for Android → Enable**,
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

The release build ships a baseline profile (startup, the welcome, the map, a guide's offer, the settings precompiled). To record it again
after the screens change: `./gradlew :app:generateBaselineProfile` runs `:baselineprofile` on a Gradle-managed
emulator (Pixel 6, API 34, downloaded on first run) and writes `app/src/main/generated/baselineProfiles/`, which is
committed. The test tags it drives are `later`, `guide-count`, `guide-sample-bear`, `guide-detail`, `settings` and
`settings-screen`: they must stay testTags **and** resource ids (`Modifier.testTag` with
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
