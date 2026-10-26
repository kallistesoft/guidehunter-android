# Follow-up

What the MVP leaves for later, most urgent first.

## Before the first Play upload

1. **Name, package and icon.** "GuideHunter" and `ca.kallistesoft.guidehunter` are working names; the launcher icon
   and the theme are BetterHunter's. Choose them before the first upload (the package can't change after).
2. **Firebase, Maps and AdMob** set up (README, "Setup"), and the CI secrets and variables added.
3. **Privacy policy and Play's Data safety form**: the app reads the approximate location (on the device only, never
   sent), shows ads (AdMob, with consent), and sends what guides type in the form.
4. **Ad policy check**: a rewarded ad must be optional and say what it gives ("Watch an ad to see the contact" does).
5. Test the release build on the emulator (`.claude/skills/testing-on-emulator`).

## Protecting the contacts

The ad is only checked in the app: `guideContacts` documents can be read by anyone who knows a guide's id. Fine for
an MVP; before it matters:

- **AdMob server-side verification (SSV)** of rewarded ads, calling a Cloud Function that returns the contact only
  for a verified reward.
- **Firebase App Check** (Play Integrity), so only the real app reaches Firestore.
- Rate limits on applications (App Check, then a Cloud Function instead of client writes).

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

## Tooling

- Screenshot tests (Roborazzi) and instrumented flow tests, as in BetterHunter, once the screens settle.
- Match the app to the mockup (`design/`): the pin colours (teal for fishing, gold for both), the grey bars in the
  hidden contact card, and a GuideHunter icon for the splash.
