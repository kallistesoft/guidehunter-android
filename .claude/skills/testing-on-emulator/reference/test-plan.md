# What to test

Pick the flows that cover the change you are testing; run the whole smoke test before a release or when asked to
"test the app". Screens are in `app/src/main/kotlin/ca/kallistesoft/guidehunter/GuideHunterApp.kt` (navigation) and
`feature/*/`; if a label below no longer exists, the screen changed: find the new label in `strings.xml` and test
the screen as it is now.

Without `app/google-services.json` the build shows three sample guides (Abitibi, La Tuque, Gaspé) and the form
can't be sent; without `MAPS_API_KEY` the map is grey. Say which of the two the tested build had.

## Contents
- Smoke test
- Checks for every screen
- Checks for a release build

## Smoke test

Start from `install.sh <type> --fresh`. Name screenshots `build/emulator-test/<nn>-<screen>[-fr|-dark].png`.

| # | Flow | Steps | Expect |
|---|---|---|---|
| 1 | Map | Launch | Map of southern Quebec, a pin per guide (orange hunting, green fishing, yellow both), guide cards at the bottom, "3 guides" |
| 2 | Filters | Tap "Fishing", then "Species" → "Walleye", then "All" | Pins and cards narrow to the matching guides, the count follows; "Any species" and "All" bring them back |
| 3 | Distances | Tap the location button (`desc=Show distances from my location`) → allow | Cards show "About … km from you", nearest first; the map moves to the user |
| 4 | Guide offer | Tap a card, then a pin | Sheet with activity, title, place, price, species, description; contact hidden behind "Watch an ad to see the contact" |
| 5 | Unlock | "Watch an ad to see the contact" → watch Google's test ad to the end → close | Name, phone, email and website; each opens the dialer, the mail app, a Custom Tab |
| 6 | Ad closed early | On another guide, close the test ad before the reward | "The ad was closed before the end…", button to watch again |
| 7 | Stays unlocked | Close the sheet, reopen the guide from step 5 | Contact shown at once, no second ad |
| 8 | Become a guide | Top bar "Become a guide" → "Send my application" with nothing filled | Every required field flagged, "Some fields need your attention." |
| 9 | Form | Fill every field, tap the map to place the pin, send | Firebase build: "Application sent", and a `guideApplications` and a `mail` document in Firestore. Sample build: "Applications can't be sent from this version of the app yet." |
| 10 | Back | System back from the form | The map, filters kept |
| 11 | French | `cmd locale set-app-locales $PKG --locales fr-CA`, revisit 1, 4, 8 | Every string in French ("Ours noir", "Doré"); no English left, no clipped text |
| 12 | Dark theme | `cmd uimode night yes`, revisit 1, 4, 8 | Dark palette, dark map, readable contrast |
| 13 | Process death | With a guide's sheet open, and again halfway through the form: HOME, `am kill`, relaunch (see driving.md) | Reopens on the same sheet (unlocked contact reloaded without an ad) or the same form, fields kept |

Don't tap "Call" in the dialer or "Send" in the mail app: they would call or email the guide.

## Checks for every screen

From the screenshot:
- Text: nothing clipped, overlapping or cut by the status/navigation bar (edge-to-edge); French strings are longer
- The bottom sheet and the form keep their content above the navigation bar and the keyboard

From `screen_text.py`:
- Every tappable element has a label (text or desc): an unlabelled icon button is an accessibility bug

## Checks for a release build

Run the smoke test on `install.sh release --fresh` before any Play upload. R8 problems show up only here:
- The map, its pins and the sheet (Google Maps, Compose)
- The rewarded ad (AdMob) and the consent form
- Guides from Firestore, and a form sent (Firebase build)
- `check_crash.sh release` after every flow; it also flags `ClassNotFoundException` and `SerializationException`
