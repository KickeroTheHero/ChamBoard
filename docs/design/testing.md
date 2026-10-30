# Testing and evaluation

How the project shows its protections work, beyond unit tests of a single class. Unit-test
conventions are in [STYLE.md › Tests](../STYLE.md#tests).

## Evaluation tests in core

- `StyleFingerprintTest` measures the habit gap between two sample authors (Canberra distance
  over habit and function-word features) before and after normalisation, and locks in the
  reduction per style level.
- Timing independence and mask variety are asserted in `PlaybackPlannerTest` and the mask tests
  (INV-6, MASK-5).
- They are fast regression checks; the [adversarial page](#adversarial-test-page) measures the
  same things the way a tracker would.

## Screenshots from CI

- The `screenshots` CI job renders the real keyboard views on a `pixel_6` emulator through
  `ScreenshotInstrumentation`, a plain `Instrumentation` with no test libraries, at the device's
  width, plus the launcher icon.
- PNGs are uploaded as a workflow artifact and, outside pull requests, also force-pushed to the
  orphan `ci-screenshots` branch (`git fetch origin ci-screenshots`). Never merge that branch.
- `FLAG_SECURE` (UI-5) stays on in every build; the runner draws views directly.
- The emulator occasionally times out; rerun the job once before debugging.

## Adversarial test page

- A local, self-hosted web page acting as the attacker. It records `keydown`/`beforeinput`/`input`
  timestamps and draft snapshots, and runs simple keystroke-dynamics and stylometry matchers,
  plus a "human or ChamBoard?" classifier.
- It reports what it could learn with and without ChamBoard.
- It never leaves the tester's machine: no hosting, no collection of real users' text (INV-3).
- Lives in `adversary/`: plain HTML and JavaScript, no build step, no packages, no network
  calls; opened from disk or served on localhost. Contents: `recorder.js` + `index.html`, and
  `matchers/` (keystroke, stylometry, classifier). Matchers are JavaScript modules tested with
  `node --test 'adversary/**/*.test.js'`.
- On simulated sessions the classifier spots ChamBoard playback by its missing word
  composition ([engine.md › Known limits](engine.md#known-limits)).
