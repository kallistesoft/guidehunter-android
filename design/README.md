# GuideHunter design mockup

The visual reference for GuideHunter's screens, in BetterHunter's theme (its "field guide" palette and Barlow fonts),
which `core/designsystem` implements.

- **Live canvas (source of truth):** https://claude.ai/artifact/PCFgbxtcGyHHrSPPPRqKbL (a claude.ai Design canvas,
  private to its owner). Add new screens there, then copy the files back here.
- **Backup copy:** `mockup/`: the canvas index (`canvas.json`) and one `.dc.html` file per screen, as published.

Phone frames are 390×844. Each page has a dark row on top and a light row below; screens link to each other, so
the flows can be clicked through on the canvas.

## How the files are built

The screens with real content are components with a `theme` prop (`dark`, `light`) and, for some, a state prop. The
other artboards are small wrappers that `<dc-import>` them with another theme or state:

| Component | Props | Wrappers |
|---|---|---|
| `Main.dc.html`: the map (header, filters, pins, guide cards) | `mode`: `all`, `fish`, `near` (distances, user dot) | `MapLight`, `MapNear`, `MapNearLight`; the backdrop of the picker, the dialog and the sheet |
| `Splash.dc.html` | | `SplashLight` |
| `SpeciesPicker.dc.html`: Fishing chosen, species menu open | | `SpeciesPickerLight` |
| `LocationAsk.dc.html`: Android's location permission | | `LocationAskLight` |
| `GuideSheet.dc.html`: a guide's offer | `contact`: `locked`, `unlocked`, `notEarned`, `offline` | `GuideSheetLight`, `SheetUnlocked…`, `SheetNotEarned…`, `SheetOffline…` |
| `RewardedAd.dc.html`: the ad (AdMob's, not ours: the same in both themes) | | `RewardedAdLight` |
| `Form.dc.html`: Become a guide | `view`: `empty`, `errors`, `filled`, `contact`, `failed` | `FormLight`, `FormErrors…`, `FormFilled…`, `FormContact…`, `FormFailed…` |
| `Sent.dc.html`: application sent | | `SentLight` |

## Pages

| Page | Screens |
|---|---|
| 1 · Find a guide | 1 Splash → 2 Map → 3 Fishing, species picker → 4 Location permission → 5 Map, nearest first → 6 Guide offer, contact hidden → 7 Rewarded ad → 8 Contact unlocked (8b ad closed early, 8c offline) |
| 2 · Become a guide | 1 Form → 2 Sent empty: errors → 3 Species, description, pin (scrolled) → 4 Price, contact, send (4b couldn't send) → 5 Application sent |

## Decisions shown in the mockup

- **Pins:** orange for hunting, teal for fishing, gold for both; they differ in lightness too, not only in hue.
  (The app's pins use Google's default marker hues: orange, green, yellow.)
- **The contact card** shows grey bars where the name and contact will be, so it's clear something is hidden.
- **Map:** the mockup draws a stylised southern Quebec. The app shows Google Maps (Compose), dark map style in dark
  theme.
- **Sample guides** are made up (`*.example` addresses); real ones come from Firestore.
