# Follow-up

What the MVP leaves for later, most urgent first.

## Before the first Play upload

1. **Name, package and icon.** "GuideHunter" and `ca.kallistesoft.guidehunter` are working names; the launcher icon
   and the theme are BetterHunter's. Choose them before the first upload (the package can't change after).
2. **Firebase, Maps and AdMob** set up (README, "Setup"), and the CI secrets and variables added.
3. **Privacy policy**, linked from the Play listing and from the app (an About entry, and a line above the form's
   Send button). It covers: the approximate location (read on the device only, never sent), ads (AdMob, with
   consent; the advertising ID and the `AD_ID` permission come with the ads SDK), and what guides type in the form
   (name, email, phone, website, coordinates: stored in Firestore and emailed through the SMTP service).
4. **Play's Data safety form**, saying the same: device IDs, approximate location and interactions collected and
   shared by AdMob for advertising; personal info and a location typed in the form, collected. Plus the Advertising
   ID declaration.
5. **Ad policy check**: a rewarded ad must be optional and say what it gives ("Watch an ad to see the contact" does).
6. **AdMob IDs set** for the release build (`ADMOB_APP_ID`, `ADMOB_REWARDED_ID`): without them the build only warns
   and serves Google's test ads.
7. Test the release build on the emulator (`.claude/skills/testing-on-emulator`): the rewarded ad, the map, and a
   relaunch after process death ("Become a guide" open) for the Navigation 3 keys under R8.

## Protecting the contacts

The ad is only checked in the app: `guideContacts` documents can be read by anyone who knows a guide's id, and the
ids are the same as the public `guides` documents', so the whole collection can be read one guide at a time. Fine
for an MVP; before it matters:

- **AdMob server-side verification (SSV)** of rewarded ads, calling a Cloud Function that returns the contact only
  for a verified reward.
- **Firebase App Check** (Play Integrity), so only the real app reaches Firestore.
- Rate limits on applications (App Check, then a Cloud Function instead of client writes). Today anyone with the
  app's Firebase config can create `mail` documents (to your address only, bounded in size) without limit, which
  can flood your inbox or use up the SMTP quota: prefer a dedicated mailbox or SendGrid over a personal Gmail.
- A `mail` document that doesn't come with its `guideApplications` document is accepted; a Cloud Function that
  writes both is the fix.

## Guides

- An approval flow instead of copying documents in the console: a small admin page, or a Cloud Function that
  publishes an application marked approved.
- Guide accounts (sign-in) so guides edit their own offer, photos, seasons, languages spoken, availability.
- Bookings and payments, then reviews, like Airbnb.

## Users

- Search by place ("hunts near Paris" is far; "in Quebec" is a region), sort by price.
- Favourites, without an account at first.
- More languages for visitors (Spanish, German).
- Region-specific species (and outside Canada), loaded from Firestore instead of the `Species` enum.

## iOS

`:core:model` is pure Kotlin on purpose. The plan: turn it (and the repositories) into Kotlin Multiplatform modules
shared with a SwiftUI app, with Firebase's iOS SDK and Google Maps for iOS, like `kallistesoft/betterHunter-ios` for
BetterHunter.

## Large screens and accessibility

- Tablets and unfolded foldables: show the guide's offer beside the map (Navigation 3 scenes, a `SupportingPane`
  strategy) instead of a bottom sheet, and collapse the card row when the window height is compact (a phone in
  landscape leaves little map).
- The form's "pick on map" needs a way that doesn't need a tap on the map (a "Use my location" button, or
  latitude/longitude fields) for TalkBack and switch users; a map inside a scrolling form also fights the scroll.
- A `scrollBehavior` on the form's top bar.

## Tooling

- Screenshot tests (Roborazzi) and instrumented flow tests, as in BetterHunter, once the screens settle.
- A baseline profile module (cold start → map → open a guide), which `profileinstaller` is waiting for.
- Compose compiler reports, to check the map's stability (the guide lists come from the Kotlin module, so Compose
  compares them by instance).
- Match the app to the mockup (`design/`): the pin colours (teal for fishing, gold for both), the grey bars in the
  hidden contact card, and a GuideHunter icon for the splash.
