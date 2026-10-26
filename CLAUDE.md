# Notes for Claude

GuideHunter: a map of hunting and fishing guides, whose contact is unlocked by a rewarded ad, and a "Become a guide"
form emailed for review. Same stack and conventions as BetterHunter (kallistesoft/betterHunter-android). See
README.md for the architecture and the setup, FOLLOW-UP.md for what's next.

## Design

There is no GuideHunter mockup yet: the theme is BetterHunter's palette and fonts. When one is made, give every
screen a dark and a light version, and keep it in `design/`.

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
