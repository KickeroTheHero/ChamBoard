# Design docs

The specification for developers. Requirement IDs defined here (INV-, BUF-, MASK-, STY-, TIM-,
POL-, CLIP-, UI-) are cited in source comments and test names.

- [engine.md](engine.md): purpose, invariants (INV), architecture, staging buffer (BUF), masks
  (MASK), style normaliser (STY), synthetic playback (TIM), modes and field policy (POL),
  clipboard (CLIP), known limits.
- [ui.md](ui.md): the Android IME shell, keyboard layout and behaviour (UI), platform and budgets.
- [testing.md](testing.md): evaluation tests, CI screenshots, the adversarial test page.
- [build.md](build.md): Gradle build, local check, CI, release.
- Coding conventions: [../STYLE.md](../STYLE.md).
