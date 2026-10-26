# Notes for Claude

GuideHunter: a map of hunting and fishing guides, whose contact is unlocked by a rewarded ad, and a "Become a guide"
form emailed for review. Same stack and conventions as BetterHunter (kallistesoft/betterHunter-android). See
README.md for the architecture and the setup, FOLLOW-UP.md for what's next.

## Design mockup

The GuideHunter design mockup (dark and light screens, BetterHunter's theme) is a claude.ai Design canvas:
https://claude.ai/artifact/PCFgbxtcGyHHrSPPPRqKbL

To add or change screens, read the canvas with the Artifact tool (`action: "read"`, then its `project/canvas.json` and
the screens' `project/*.dc.html` files) and publish to that same URL; don't create a new canvas. Follow the palette,
fonts and patterns in `design/README.md`, and give every new screen a dark and a light version. After publishing,
copy the changed files into `design/mockup/` and update `design/README.md`.

## Skills

Repo skills live in `.claude/skills/`. Read the skill's `SKILL.md` and follow it when the task matches:

- [testing-on-emulator](.claude/skills/testing-on-emulator/SKILL.md): build, install and test the app on the Android
  emulator (smoke test, a change, a release/R8 build before a Play upload)
- [viewmodel-state](.claude/skills/viewmodel-state/SKILL.md): how ViewModels build their state (no `init`; `combine` +
  `stateIn`, or `asStateFlow().onStart { }` + `stateIn`), enforced by `scripts/check-conventions.sh`
- [unit-testing](.claude/skills/unit-testing/SKILL.md): unit tests (JVM, Robolectric Compose) and Kover coverage, 90% of
  lines for every class (`./gradlew :koverVerifyUnit`); use with any code change and when the Coverage step fails

The other skills in `.claude/skills/` are Google's Android skills (edge-to-edge, Navigation 3, R8, Play policy…),
copied from BetterHunter.
