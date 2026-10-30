# Contributing to ChamBoard

Thanks for helping. This page covers what you need to build, test and send a change.

## The one rule

**The keyboard never persists, logs or transmits what the user types, and declares no
permissions.** The detailed invariants are INV-1..7 in
[`docs/design/engine.md`](docs/design/engine.md). They are checked by tests and CI. If a change
makes an invariant check fail, fix the change; don't weaken the check.

In practice that means:

- no `<uses-permission>` in the manifest (the `verifyNoPermissions` task fails the build);
- no Android or network imports and no runtime dependencies in `core/`;
- no logging in `app/`, and the system clipboard is used only in `ClipboardBridge.kt`;
- nothing is learned from what the user types.

## Layout

| Path | What it is |
|---|---|
| `core/` | The engine: pure JVM Kotlin, no dependencies. Every decision about text, style and timing lives here. |
| `app/` | The Android IME shell: views, input connection, settings. Kept thin. |
| `app/src/androidTest/` | The screenshot runner used by CI. |
| `tools/appcheck/` | Compiles `app/` against a plain `android.jar` for fast checks without the Android SDK. |
| `adversary/` | A local web page that plays a tracker, with its matchers and `node --test` tests. |
| `scripts/check` | The one-line project check. |
| `docs/` | The guide, architecture, design spec and style guide. |

More detail in [`docs/STYLE.md`](docs/STYLE.md).

## Requirements

- JDK 17
- Node.js 22+ (for the test-page tests; skipped if missing)
- The Android SDK (platform 35) to build the app itself

## Build and test

```sh
scripts/check               # format, detekt, core tests, invariants, test-page tests
scripts/check fix           # apply formatting
scripts/check test '*StyleEngine*'   # one test class or method
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug verifyNoPermissions
```

`scripts/check` prints one line starting `✓ check:` when everything passes. It builds only the
engine (`-PcoreOnly=true`), so it works without the Android SDK. If an `android.jar` is available
(`$ANDROID_JAR`, or a local SDK), it also compile-checks the app code. For that to work, app code
must not reference the generated `R` or `BuildConfig` classes.

The full app build, Android Lint, the no-permissions check and emulator screenshots run in GitHub
Actions (`.github/workflows/ci.yml`) on pushes to `main` and on pull requests.

## Sending a change

- Keep `core/` free of platform code; put Android glue in `app/`.
- Add or update tests. Tests cite the requirement ID they cover (e.g. `STY-3`), so a spec line and
  its tests can be found together.
- If you change behaviour, update the matching section of [`docs/design/`](docs/design/) and, if
  users would notice, [`docs/guide/status.md`](docs/guide/status.md).
- Run `scripts/check` before opening a pull request, and fill in the template.

## Licence

By contributing you agree that your contribution is released into the public domain under
[The Unlicense](LICENSE).
