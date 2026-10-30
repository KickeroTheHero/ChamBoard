# IME shell and UI

What `app/` does: the Android input-method shell and everything the user sees. All decisions live
in `core` ([engine.md › Architecture](engine.md#architecture)); the invariants are in
[engine.md › Invariants](engine.md#invariants).

## The shell

- `InputMethodService` (`ChamImeService`) wires lifecycle events, views and the emitter.
  `DraftController` turns keys into buffer edits and emits; `Emitter` drives a `PlaybackSession`
  on a cancellable `Handler`.
- Views are built in code: `KeyboardView`, `DraftPanel`, `ControlBar`, `SuggestionStrip`. No
  AndroidX, no libraries, no references to generated `R` or `BuildConfig` (the local compile
  check against `android.jar` can't see them; see [build.md](build.md#build)).
- Physical keyboards: in staging modes, `HardwareKeys` maps key events to draft edits (BUF-1;
  Ctrl/Meta+Enter emits, dead keys combine) and the input view stays shown; in Passthrough they
  reach the field untouched (POL-4).
- Accessibility: `KeyboardAccessibility` exposes each key as a virtual view with a spoken name and
  explore-by-touch (UI-1..3); chips carry TalkBack descriptions.
- The launcher icon is an adaptive icon in `res/mipmap-anydpi-v26/`, referenced only from the
  manifest.
- `ClipboardBridge.kt` is the only code that touches the system clipboard (INV-3).
- `Store` holds preferences: settings and mask state only, never text (INV-3, UI-4).

## Keyboard layout and behaviour (UI)

```
+-----------------------------------------------------------+
| [FULL CLOAK ▾]   Style: Standard   Pace: Glide   [✕ wipe] |
| Draft:  The proposal wasnt particularly workable...|      |
| Sends:  The proposal wasn't particularly workable...      |
| [q][w][e][r][t][y][u][i][o][p]                            |
|  [a][s][d][f][g][h][j][k][l]                              |
| [⇧][z][x][c][v][b][n][m][⌫]                               |
| [?123] [,] [      space      ] [.] [ EMIT ▶ ]             |
+-----------------------------------------------------------+
```

- **UI-1** Draft line shows the buffer with cursor; Sends line highlights changed spans (Full Cloak only).
- **UI-2** EMIT becomes STOP during playback, with progress.
- **UI-3** Tap mode chip to cycle; long-press for Passthrough bypass. Chip shows when the field ceiling lowered the mode.
- **UI-4** Settings screen: enable-keyboard walkthrough, default mode, style level, mask management (MASK-2) or Uniform, playback style, entropy, friction toggles (UI-6, UI-7, BUF-3, CLIP), try-it box. Preferences store settings and mask state only, never text (INV-3).
- **UI-5** Keyboard window uses `FLAG_SECURE` where supported, so drafts don't appear in screenshots or recordings.
- **UI-6 Enter = Emit & Send** (toggle; default on): in chat/search fields the Enter/action key emits and then performs the field's action in one tap.
- **UI-7 Offline suggestions** (toggle; default on): word completions from a built-in static dictionary. No learning of personal words; nothing stored.
- **UI-8 Friction principle.** Absolute rules are only INV-1..7. Everything else that costs convenience is a toggle with a sensible default, and the settings screen explains the trade-off in one line.

## Platform and budgets

- `InputMethodService`, min SDK 29, target/compile SDK 35, Kotlin, no AndroidX, no runtime libraries.
- RAM < 45 MB; Passthrough key-to-commit < 16 ms.
- Distribution: GitHub releases and F-Droid ([build.md › Release](build.md#release)).
