# Follow-up

What the MVP leaves for later, most urgent first.

## Before the first Play upload

1. **Name, package and icon.** "GuideHunter" and `ca.kallistesoft.guidehunter` are working names; the launcher icon
   and the theme are BetterHunter's. Choose them before the first upload (the package can't change after).
2. **Firebase, Maps and AdMob** set up (README, "Setup", step by step), and the CI secrets and variables added. A
   **release keystore** too: the debug key lives on whichever machine builds, so the Maps key and Firebase need the
   release key's SHA-1 for anything installed from Play.
3. **Privacy policy**, linked from the Play listing and from the app (an About entry, and a line above the form's
   Send button). It covers: the approximate location (read on the device only, never sent), ads (AdMob, with
   consent; the advertising ID and the `AD_ID` permission come with the ads SDK), and what guides type in the form
   (name, email, phone, website, coordinates: stored in Firestore and emailed through the SMTP service), and guides'
   Google account (its email and a Firebase user id) and how they delete it.
4. **Play's Data safety form**, saying the same: device IDs, approximate location and interactions collected and
   shared by AdMob for advertising; personal info and a location typed in the form, collected; guides' account (email,
   user id), collected. **Since requests (2026-10-28):** every signed-in user's name, email and optional phone,
   collected and **shared with the guide** they send a request to (and a guide's contact shared with users whose
   request they accept). Plus the Advertising ID declaration, and Play's **account deletion** answers: in the app
   (Delete my account), and a web page or email address for guides who uninstalled it (Play requires one).
5. **Ad policy check**: a rewarded ad must be optional and say what it gives ("Watch an ad to contact the guide" does).
6. **AdMob IDs set** for the release build (`ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_NATIVE_ID`, `ADMOB_BOOST_ID`): without them
   the build only warns and serves Google's test ads. Create the **native ad unit** in GuideHunter's AdMob app (for
   the ad cards in the lists) and set `ADMOB_NATIVE_ID` as a CI variable and in `local.properties`. Likewise a second
   rewarded unit "Boost" (so the boosts' revenue is reported apart) for `ADMOB_BOOST_ID`.
7. **New-offer emails need Blaze.** Both Firebase projects are on the free Spark plan (checked 2026-10-28), where
   the Trigger Email extension can't run: offers and edits are saved, but the review email is never sent. Either move
   the prod project to Blaze (about $0 at this volume; add a **$5 budget alert** first) and install the extension,
   or check pending offers by hand with `scripts/approve-offer.py`.
8. Test the release build on the emulator (`.claude/skills/testing-on-emulator`): the rewarded ad, the map, and a
   relaunch after process death (a guide's offer open) for the Navigation 3 keys under R8.

## Requests

- **Push notifications** (FCM) for a new request (to the guide) and a status change (to the user); today each side
  sees them when they open the app.
- An optional **email to the guide** on each new request, through Trigger Email. The `mail` rule only allows mail to
  contact@ today, so this needs its own careful rule, or better a Cloud Function on `requests` writes.
- **Check the ad on the server**: the ad before "I'm interested" is only checked in the app, so anyone signed in could
  send requests to every offer without one (one request per offer limits it). AdMob SSV and App Check, below.
- Rules tests on the Firestore emulator (`@firebase/rules-unit-testing`) for the request cases: a wrong `guideUid` or
  `offerTitle`, another email than the account's, one's own offer, a status change by the wrong side,
  `OFFER_REMOVED` set by the user.

## Protecting the server

The ads are only checked in the app. Before it matters:

- **AdMob server-side verification (SSV)** of rewarded ads, calling a Cloud Function that creates the request only
  for a verified reward.
- **Firebase App Check** (Play Integrity), so only the real app reaches Firestore.
- Rate limits on offers (App Check, then a Cloud Function instead of client writes). Today any signed-in Google
  account can create `mail` documents (to your address only, bounded in size) and offers without limit, which can
  flood your inbox or use up the SMTP quota: prefer a dedicated mailbox or SendGrid over a personal Gmail.
- A `mail` document that doesn't come with an offer is accepted; a Cloud Function that sends the email when an
  offer is written (a Firestore trigger) is the fix, and lets the app stop writing `mail` at all.

## Offer boost

A guide features a live offer for 3 days by watching a rewarded ad (README, "Offer boost"). For now:

- **The ad is only checked in the app.** The rule bounds a boost to 3 days ahead, but a technical guide could renew
  it without watching the ad. The fix: AdMob **server-side verification (SSV)** of the boost unit calling a Cloud
  Function (the projects on Blaze, with a **$5 budget alert** first), which then writes `boostedUntil` alone; remove
  the owner's `update` rule on `guides` then. The same function can check the contact's ad (above).
- A **paid boost** later (Play Billing), longer or always first.
- Create the AdMob "Boost" rewarded unit and set `ADMOB_BOOST_ID` (local.properties and the CI variable).

## Guides

- Approval is `scripts/approve-offer.py` (README, "Firestore"); a small admin page would let someone else review.
- A **refused** status the guide sees in the app, with our reason (today we reply by email and the offer stays
  "waiting for review").
- Guides' offers: photos, seasons, languages spoken, availability.
- Release: add the release key's SHA-1 (and Play App Signing's) to the production Firebase app, then download its
  `google-services.json` again, or Google sign-in fails in release builds.
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

- On a wide window a guide's offer opens beside the map (done, as a layout switch in the map screen). The Navigation 3
  scene version (a `SupportingPane` strategy, the offer as its own `NavKey`) would also give it its own back-stack
  entry; worth it once there are more destinations.
- Collapse the card row when the window height is compact (a phone in landscape leaves little map).
- A `scrollBehavior` on the form's top bar.

## Tooling

- Screenshot tests (Roborazzi) and instrumented flow tests, as in BetterHunter, once the screens settle.
- Record the baseline profile (`./gradlew :app:generateBaselineProfile`, README "Baseline profile") on a machine
  with an emulator, and commit `app/src/main/generated/baselineProfiles/`; the module and the generator are in place.
- Compose compiler reports, to check the map's stability (the guide lists come from the Kotlin module, so Compose
  compares them by instance).
- The tablet mockup outlines the open guide's card and enlarges its pin; the app doesn't yet.
