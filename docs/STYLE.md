# Style and organization

Coding conventions for ChamBoard. Rules a tool enforces live in that tool's config
([Enforced by tools](#enforced-by-tools)).

## Characteristics

- Kotlin only. `core`: pure Kotlin/JVM, no dependencies, no Android, no I/O. `app`: an Android
  `InputMethodService` shell, SDK 29–35, no AndroidX, no libraries
  ([design/ui.md › Platform and budgets](design/ui.md#platform-and-budgets)).
- Gradle with a version catalog; bytecode targets Java 17, builds on any JDK ≥ 17.
- The full APK needs the Android SDK (`./gradlew :app:assembleDebug`). Without it, `scripts/check`
  builds `core` and compile-checks `app` against an `android.jar` ([design/build.md](design/build.md)).
- Small codebase (about 4k lines of Kotlin).
- The product is a privacy tool: every convention below serves INV-1..7
  ([design/engine.md › Invariants](design/engine.md#invariants)).

## Layout

| Path | What goes there |
| :--- | :--- |
| `core/src/main/kotlin/dev/chamboard/core/` | `buffer/` (draft, private clipboard, lifecycle), `mask/`, `policy/`, `settings/`, `style/` (+ `lexicon/`), `suggest/` (+ `lexicon/`), `timing/`. All decisions. |
| `core/src/test/kotlin/…` | tests mirroring the main package path |
| `app/src/main/kotlin/dev/chamboard/ime/` | `ChamImeService` (lifecycle), `DraftController` (keys/emit), `Emitter`, views (`KeyboardView`, `DraftPanel`, `ControlBar`), `ChipActions`/`ClipActions`, `ClipboardBridge` (only system-clipboard code), `Store` (prefs), `SettingsActivity` |
| `app/src/main/res/`, `AndroidManifest.xml` | only what the manifest needs; views are built in code |
| `app/src/androidTest/` | `ScreenshotInstrumentation` |
| `tools/appcheck/` | local compile + detekt of app sources against `android.jar` |
| `adversary/` | the adversarial test page: plain HTML + JS, no packages; classic scripts that also `require()` in Node (ES modules don't load from `file://`); tests in `adversary/test/*.test.js` |
| `scripts/` | `scripts/check` is the developer entry point |
| `gradle/`, `config/`, `*.gradle.kts`, `.github/workflows/ci.yml` | build, lint config, CI |
| `docs/` | `docs/design/` is the specification; `docs/guide/` is the plain-language user guide |

- New logic goes in `core` first. If `app` needs a decision, add it to `core`, where it can be
  unit-tested on a plain JVM.
- Entry points: `ChamImeService` (app), `scripts/check` (developer).

## Naming

| Thing | Pattern | Example |
| :--- | :--- | :--- |
| Kotlin file | one main type, file named after it | `PlaybackPlanner.kt` |
| Package | `dev.chamboard.core.<area>`, `dev.chamboard.ime` | `dev.chamboard.core.timing` |
| Types | specific nouns, no `Manager`/`Helper`/`Utils` | `DraftParking`, `ClipboardBridge` |
| Test class | `<Subject>Test` | `FieldPolicyTest` |
| Test method | backticks-free camelCase describing behaviour | `contractedAndExpandedInputsNormaliseIdentically` |
| Requirement IDs | `<FAMILY>-<n>`, defined in `docs/design/` | `INV-5`, `STY-3` |

Prefer specific, searchable names: one text search should find the definition.

## Modules

- Files: about 300 lines soft, 500 hard. Lexicon files may run longer; they are data.
- Prefer small pure functions and immutable data classes in `core`.
- Dependency direction: `app` → `core`, never back. `core` has no runtime dependencies (INV-2).
- Randomness is always injected (`kotlin.random.Random` parameter) so tests are deterministic;
  production uses `SecureRandom`-backed instances. Anything derived from a mask seed uses
  `StableRandom`, never `Random(seed)`: mask outputs must never change across Kotlin versions.
- `app` never references generated `R` or `BuildConfig`: the local compile check against
  `android.jar` can't see them.
- Import from the module that defines a thing; no re-export layers, no hidden registration or
  metaprogramming.

## Tests

- JUnit 4 in `core/src/test/` and `app/src/test/`, mirroring the main package path.
- Test names describe behaviour; new behaviour has tests citing its requirement ID in a comment
  or name (for example `/** Physical-keyboard keys edit the draft in staging modes (BUF-1). */`).
- Run everything: `scripts/check`. One class or method: `scripts/check test '*Buffer*'`.
- App unit tests need the Android SDK: `./gradlew :app:testDebugUnitTest` (CI runs them).
  Evaluation tests: [design/testing.md](design/testing.md#evaluation-tests-in-core).
- Adversarial page: `node --test 'adversary/**/*.test.js'` (also run by `scripts/check` and CI).

## Errors, logs and config

- **No logging anywhere in `app`** (INV-3); CI greps for `Log`. `core` has no I/O at all.
- Errors in `core` are exceptions or result types that say what failed and why, never including
  user text.
- Config is user settings in `Store` (SharedPreferences), settings and mask state only. There are
  no runtime environment variables. Build-time: `ANDROID_JAR` or `ANDROID_HOME` (optional, read
  by `scripts/check`).

## Dependencies

- No new Gradle dependency (build or runtime) without discussing it in an issue first. `core` may
  never have one (INV-2); `app` ships none.
- Lexicons (spelling maps, synonym sets, wordlists) are Kotlin source or resources in `core`,
  reviewed like code; keep each entry defensible.

## Comments

Comments explain *why*, in plain language a non-programmer can follow. Cite the requirement ID a
line exists for (`// INV-2: …`). KDoc on public types in `core`. Don't strip comments for
brevity.

## Enforced by tools

| Tool | Config | Command |
| :--- | :--- | :--- |
| ktlint (via Spotless) | `.editorconfig` (Kotlin official, 120 cols, no wildcard imports), version in `gradle/libs.versions.toml` | `scripts/check fix` |
| Spotless misc (trailing whitespace, final newline on md/yml/sh) | `build.gradle.kts` | `scripts/check` |
| detekt | `config/detekt.yml` (every override needs a reason comment) | `scripts/check` |
| Invariant checks (INV-2) | `CoreIsolationTest`, `:core:verifyNoRuntimeDeps` | `scripts/check` |
| Invariant checks (INV-1, INV-3) | `:app:verifyNoPermissions`; CI grep for `Log` and `ClipboardManager` | CI `app` job, or `./gradlew :app:verifyNoPermissions` with an Android SDK |
| Android Lint | AGP defaults | CI `app` job, or `./gradlew :app:lintDebug` with an Android SDK |

No `@Suppress` without a reason comment.
