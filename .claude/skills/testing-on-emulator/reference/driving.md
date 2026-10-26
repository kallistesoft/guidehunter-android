# Driving the emulator

`ADB=~/Library/Android/sdk/platform-tools/adb` (adb isn't on the PATH). `PKG` is `ca.kallistesoft.guidehunter.debug`, or
`ca.kallistesoft.guidehunter` for the release build.

## Contents
- Reading the screen
- Input
- Device settings (language, dark mode, font size, rotation)
- App state (reset, process death)
- When things go wrong

## Reading the screen

```sh
android layout --flat 2>/dev/null | python3 -I $S/screen_text.py    # compact list: selector, actions, @x,y
android layout --flat --full -o build/emulator-test/layout.json      # everything, including hidden nodes
android screen capture -o build/emulator-test/<nn>-<screen>.png
android screen capture --annotate -o build/emulator-test/a.png       # numbered boxes, for elements without text
android screen resolve ...                                           # turns "#3" from an annotated shot into x,y
```

In Compose, a clickable card has no text; its label is a separate node inside it. Tapping the label taps the card.

## Input

```sh
android input tap "text=Become a guide" --wait=3000
android input tap "text=Bear hunt" --match=first        # label shown more than once (card and sheet)
android input scroll "scrollable=true" --dir=down --until="text=Send my application"
android input text "Pourvoirie" "clz=EditText"           # type into a field
$ADB shell input keyevent KEYCODE_BACK                   # system back (tests predictive back / NavDisplay onBack)
$ADB shell input swipe 540 1800 540 600 300              # scroll by hand: up the screen over 300 ms
$ADB shell input tap 540 794                             # last resort: coordinates from screen_text.py
```

After a tap that changes screens, wait for the new screen with `--wait` on the next tap, or `sleep 1` before
`android layout`; Compose animations otherwise show the old screen.

## Device settings

| Check | Command | Undo |
|---|---|---|
| French UI | `$ADB shell cmd locale set-app-locales $PKG --locales fr-CA` | `--locales en-CA` |
| Dark theme | `$ADB shell cmd uimode night yes` | `night no` |
| Large text | `$ADB shell settings put system font_scale 1.3` | `1.0` |
| Landscape | `$ADB shell settings put system user_rotation 1` (with `accelerometer_rotation 0`) | `user_rotation 0` |

Undo every setting before finishing so the next test starts from the default.

## App state

```sh
$ADB shell pm clear $PKG                       # wipe saved state and granted permissions: a clean start
# Process death: the back stack (Serializable NavKeys), the open guide and the form must survive it.
$ADB shell input keyevent KEYCODE_HOME
sleep 2                                        # am kill only kills a background app; right after HOME it does nothing
$ADB shell am kill $PKG
$ADB shell pidof $PKG || echo killed           # must print "killed", or the test proves nothing
$ADB shell am start -n $PKG/ca.kallistesoft.guidehunter.MainActivity    # should reopen on the same screen
```

## When things go wrong

| Symptom | Fix |
|---|---|
| `adb shell` or `android layout` hangs | Run `$S/emulator.sh` again: it detects an unresponsive emulator and cold-boots it |
| "No app window on screen" | App crashed or is in background: run `$S/check_crash.sh`, then relaunch with `install.sh --no-build` |
| "No matching elements" | Label is a content description (`desc=…`), is off screen (scroll to it), or the screen is in French |
| Tap succeeds but screen doesn't change | Element disabled (e.g. "Send my application" while sending), or a dialog is on top: dump the layout |
| The map is grey, no streets | No Maps key in the build: set `MAPS_API_KEY` in `local.properties` (README, "Google Maps") and rebuild |
| Only three made-up guides | No `app/google-services.json` in the build: it shows the sample guides (README, "Firebase") |
| Location permission dialog | A system dialog: tap `text=Only this time` or `text=While using the app` |
| `INSTALL_FAILED_*` | `install.sh` already handles signature mismatch; for anything else show the message to the user |
| `android` CLI missing | `brew install --cask android-commandlinetools`, or use `$ADB shell uiautomator dump` and `$ADB exec-out screencap -p > shot.png` |
