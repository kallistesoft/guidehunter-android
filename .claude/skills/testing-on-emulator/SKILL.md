---
name: testing-on-emulator
description: Builds GuideHunter, installs it on the Android emulator and tests it by tapping through its screens, reading screenshots and checking logcat for crashes. Use when asked to test, run, smoke-test, QA or check the app (or a change, screen or bug fix) on the emulator, simulator or a device, or to check a release (R8) build before uploading to Play.
---

# Testing GuideHunter on the emulator

Scripts are in `.claude/skills/testing-on-emulator/scripts/` (`$S` below). Run them; don't read them first.
Screenshots and notes go in `build/emulator-test/` (git-ignored).

```sh
S=.claude/skills/testing-on-emulator/scripts
mkdir -p build/emulator-test
```

## Workflow

Copy this checklist and tick it off as you go:

```
Emulator test:
- [ ] 1. Code matches GitHub (git fetch, branch up to date)
- [ ] 2. Emulator ready ($S/emulator.sh)
- [ ] 3. App built, installed, launched ($S/install.sh)
- [ ] 4. Flows driven, screenshot read for each screen
- [ ] 5. No crash ($S/check_crash.sh) after each flow
- [ ] 6. Report written
```

**1. Code matches GitHub.** Run `git fetch` then `git status -sb`. If the branch is behind, `git pull --ff-only`. If it
can't fast-forward or has local changes you didn't make, tell the user which code you are testing instead of
guessing.

**2. Emulator.** `$S/emulator.sh` reuses a running emulator or cold-boots AVD `BlockDaily_Pixel_9` (1080×2424) and
prints its serial. If it fails twice, stop and tell the user; don't loop.

**3. Install.** Pick the build:

| Goal | Command |
|---|---|
| Test a change (default) | `$S/install.sh debug` |
| A clean start (no location permission granted yet) | `$S/install.sh debug --fresh` |
| Before a Play upload, or anything about R8, serialization, reflection | `$S/install.sh release --fresh` |
| Reinstall without rebuilding | add `--no-build` |

Debug is `ca.kallistesoft.guidehunter.debug` ("GuideHunter Dev"); release is `ca.kallistesoft.guidehunter`. A Gradle failure stops
here: report the compile error, don't test an old APK.

**4. Drive the app.** One loop per screen:

```sh
android layout --flat 2>/dev/null | python3 -I $S/screen_text.py   # what is on screen, with tap points
android input tap "text=Become a guide" --wait=3000                  # tap by visible text
android screen capture -o build/emulator-test/02-form.png            # then Read the PNG and look at it
```

`screen_text.py` prints each element as `text="…"` or `desc="…"`: tap with that same selector (icon buttons such as
About are `desc=About`). Use coordinates (`adb shell input tap X Y`) only for elements with neither. When a label
appears twice, add `--match=first`. Labels come from `feature/*/src/main/res/values/strings.xml` and `core/ui/src/main/res/values/strings.xml` (French in
`values-fr/`). Read every screenshot you take: layout bugs (clipped text, overlap, wrong colours, untranslated
strings) only show up there, not in the layout dump. Compare against the mockup in `design/mockup/` when a screen
has one.

For what to cover, see [reference/test-plan.md](reference/test-plan.md). For back, scrolling, typing, language,
dark mode, font size, rotation and process death, see [reference/driving.md](reference/driving.md).

**5. Crashes.** Run `$S/check_crash.sh` (add `release` for the release build) after each flow. It exits 1 with
the stack trace on a crash, ANR or R8/serialization error. When it fails:

1. Read the stack trace and find the cause in the code
2. Fix it, then `$S/install.sh` again (logcat is cleared on each launch)
3. Repeat the same flow and run `$S/check_crash.sh` again
4. Go on only when it prints `OK`

If you weren't asked to fix bugs, report them instead of changing code.

**6. Report.** Use this shape:

```markdown
## Emulator test: <branch> @ <short sha>, <debug|release>
Device: BlockDaily_Pixel_9 (Android <version>)

| Flow | Result | Notes |
|---|---|---|
| Map → guides | ✅ | |
| Unlock contact | ❌ | Crash after the rewarded ad closes (stack trace below) |

Issues: one bullet per problem: screen, steps, expected vs seen, screenshot path.
Not tested: anything skipped and why.
```

Say plainly what failed or wasn't covered. Leave the emulator running at the end.
