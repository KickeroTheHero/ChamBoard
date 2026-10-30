# Build, CI and release

How the project is built, checked and shipped. There is no server: the product is an APK.

## Build

- Gradle with a version catalog (`gradle/libs.versions.toml`). Modules: `:core` (JVM), `:app`
  (Android). Bytecode targets Java 17; any JDK ≥ 17 builds it.
- Full app (needs the Android SDK): `./gradlew :app:assembleDebug`.
- `-PcoreOnly=true` excludes `:app`, so `core` builds without the Android SDK or Google's Maven
  repository. With `-PandroidJar=<path>` it also includes `:appcheck` (`tools/appcheck/`), which
  compiles and runs detekt on the app sources against an `android.jar`. This lets environments
  without the Android SDK still catch most app errors. It can't see generated `R` or
  `BuildConfig`, which is why app code never references them.
- No new Gradle dependency without discussion first ([STYLE.md › Dependencies](../STYLE.md#dependencies)).

## Local check

`scripts/check` runs Spotless, detekt, the core tests, the invariant checks, the adversarial
page tests (`node --test`), and an app compile check against `android.jar` when one is
available (`$ANDROID_JAR`, or `$ANDROID_HOME/platforms/android-35/android.jar`). It prints one
line starting `✓ check:` on success and a short summary on failure; the full Gradle output is in
`build/check.log`.

- `scripts/check fix` applies formatting.
- `scripts/check test '<glob>'` runs one test class or method, e.g. `'*StyleEngine*'`.

## CI

`.github/workflows/ci.yml` runs on pushes and pull requests.

| Job | Checks |
| :--- | :--- |
| `core` | format, detekt, core tests, invariants, adversarial page tests |
| `app` | INV-3 grep (no logging; `ClipboardManager` only in `ClipboardBridge.kt`), debug APK, Android Lint, app unit tests, `verifyNoPermissions` (INV-1); uploads the debug APK |
| `screenshots` | emulator screenshots ([testing.md › Screenshots from CI](testing.md#screenshots-from-ci)) |

## Release

There is no release workflow yet; CI produces debug APKs only. The intended setup:

- Release builds signed in CI from a key stored as a repository secret. The key never enters the
  repository, logs or build outputs.
- Distribution through GitHub releases and F-Droid with reproducible builds.
