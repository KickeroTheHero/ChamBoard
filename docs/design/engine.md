# Engine and invariants

What `core/` does, plus the invariants the whole project obeys. Requirement IDs (INV-, BUF-,
MASK-, STY-, TIM-, POL-, CLIP-, UI-) are stable: source comments, test names, commits and pull
requests cite them. UI-n requirements are defined in [ui.md](ui.md).

## Purpose

An open-source Android keyboard (IME) that stops apps and websites from learning *who you are*
from *how you type and write*.

| Leak | What the observer learns | Answer |
| :--- | :--- | :--- |
| **Drafts** | Everything typed, deleted or rewritten before send (session replay, search autocomplete, form analytics). | **Staging buffer.** Text stays inside the keyboard until the user emits it. |
| **Typing rhythm** | Timing between characters: a behavioural biometric that links accounts. | **Synthetic playback.** Emitted text uses a random, human-plausible rhythm not derived from the user's. |
| **Writing style** | Punctuation, spelling, contractions, word choice: stylometry that links pseudonymous posts. | **Style masks.** Habits are rewritten into those of a *mask*: a random, realistic writing persona, stable per app or identity ([Masks](#masks-mask)). |

Non-goals: preventing an app from reading text the user *does* send; defeating sensors, cameras
or OS-level compromise ([Known limits](#known-limits)).

## Invariants

These hold everywhere and are never weakened. A failing invariant check means the code is wrong,
not the check.

| ID | Invariant | Enforced by |
| :--- | :--- | :--- |
| INV-1 | The app declares **no permissions**, in particular no `INTERNET`. | `:app:verifyNoPermissions` in CI (fails on any `<uses-permission>` in the merged manifest). |
| INV-2 | `core` has **zero runtime dependencies** and **no `android.*` imports**. | `CoreIsolationTest`, `:core:verifyNoRuntimeDeps`. |
| INV-3 | Text is **never persisted, logged, or transmitted**. The system clipboard is touched only on an explicit user tap; exported copies are marked sensitive and auto-cleared. The private clipboard lives in memory only and expires (CLIP). Drafts are wiped on screen-off, field change and after the hide grace period (BUF-3). | `StagingBuffer`/`PrivateClipboard` wipe tests; CI grep: no `Log` in the app; `ClipboardManager` only in `ClipboardBridge.kt`. |
| INV-4 | **Sensitive fields are never rewritten**: passwords, URIs, email addresses, numbers, phone, dates, person names, postal addresses. | `FieldPolicyTest`. |
| INV-5 | **Choice replacement**: stylometric output depends on the text's content and the active mask, never on which variant the author typed. Every variant of a habit maps to the mask's form (or is sampled uniformly), never inverted. | `StyleEngineTest` (variant groups, synonym and idempotence tests), `StyleFingerprintTest`. |
| INV-6 | **Timing independence**: playback timing comes from the mask's timing persona, sampled from population-level ranges, never from the user's real key timings. | `PlaybackPlanner` has no input for real timings; `PlaybackPlannerTest` persona randomness and seeding. |
| INV-7 | Emitted text only ever reaches the **field it was drafted for**. If the target field changes mid-playback, playback stops; the unsent text and any draft are *parked* for that field (BUF-5) and never shown in another field. | `PlaybackPlannerTest` resume tests; `PlaybackSessionTest` (field-token gating, stop at every step). |

Only INV-1..7 are absolute. Everything else that costs convenience is a user toggle (UI-8).

## Architecture

```
┌──────────────── app (Android shell, thin) ────────────────┐
│ ImeService ─ KeyboardView ─ ControlBar ─ DraftPanel        │
│        │                              ▲                    │
│        ▼                              │                    │
│  Emitter (Handler, cancellable) ──► InputConnection.commitText
└────────┼──────────────────────────────┼────────────────────┘
         │ calls                        │ renders
┌────────▼──────────── core (pure Kotlin, no deps) ─────────┐
│ buffer/  StagingBuffer   — editable CharArray, wipeable    │
│ mask/    Mask            — seed → style profile + persona  │
│ policy/  FieldPolicy     — field type → allowed mode       │
│ style/   StyleEngine     — tokenise → rules → diff spans   │
│ suggest/ Suggester       — prefix → static wordlist (UI-7) │
│ timing/  PlaybackPlanner — text + persona → timed chunks   │
└────────────────────────────────────────────────────────────┘
```

All decisions (what text, which mode, what timing) live in `core`, so they are unit-testable on a
plain JVM and auditable in isolation. `app` only draws keys, forwards touches and executes a
`PlaybackPlan` ([ui.md › The shell](ui.md#the-shell)).

## Staging buffer (BUF)

- **BUF-1** While a staging mode is active, keystrokes edit an internal buffer; no `InputConnection` call is made. Host pages see no `keydown`/`input`/`beforeinput` events.
- **BUF-2** Backed by a `CharArray` with explicit cursor; on growth the old array is zero-filled. `wipe()` zero-fills. Readers use the copy-free `view` (and `CursorMarked` for display); `snapshot()` is used only where a `String` is unavoidable.
- **BUF-3** Wiped on: screen off, input field changed (after parking, BUF-5), successful emit, and keyboard hidden for longer than the **hide grace period** (toggle; default 60 s, 0 = wipe immediately). Wipe is best-effort: the JVM and the target app may hold copies ([Known limits](#known-limits)).
- **BUF-4** Hard cap of 20 000 characters.
- **BUF-5 Parking.** On field change the draft (plus any unsent playback text) is parked with that field's identity. Returning to the *same* field within the grace period restores it; any other field starts empty. Parked text is zero-filled on expiry, screen-off, or when another draft is parked.

## Masks (MASK)

A single shared house style and a single timing model are themselves signatures: everyone using
the tool would look alike, and unlike everyone else. Masks make each identity look like a
*different, ordinary* person.

- **MASK-1** A mask = a **style profile** (STY-9) + a **timing persona** (TIM-1), both sampled from population-level priors from one seed. Nothing in a mask is derived from the user (INV-5, INV-6).
- **MASK-2 Scope.** Each app (package) gets its own mask automatically. Users can create **named masks** ("Reddit throwaway"), pin one to an app, and **rotate** any mask on demand. A mask stays stable until rotated; different identities never share one.
- **MASK-3 Derivation, not storage.** `seed = HMAC-SHA256(deviceSecret, scope ‖ rotation)`. Stored state is only: the random device secret, per-scope rotation counters, named-mask names and pins. Scope keys are stored as HMACs, so preferences don't list which apps were used. No text is ever stored (INV-3).
- **MASK-4 Uniform option.** The fixed canonical profile (US spelling, contractions, "haha", `:)` …) stays available as "Uniform". It pays off only when many people use it (crowd anonymity); masks are the default.
- **MASK-5 Variety check.** Tests assert that masks differ from each other in style and timing (the tool must not have one recognisable output), while each mask is stable.

## Style normaliser (STY)

Principle (INV-5): *replace the author's choices with the mask's*. The level decides **which**
habits are overwritten; the mask's profile decides **what** they become.

Pipeline: tokenise → protect spans → apply rules by strength level → emit text + list of changed
spans (for the preview).

- **STY-1 Protected tokens** are never changed: URLs, emails, @handles, #tags, numbers, inline code (`` `...` ``), text inside quotes the user explicitly marks.
- **STY-2 Light level** (default): whitespace (single spaces, no space before `,.!?;:`), straight quotes, canonical ellipsis `...`, collapse `!!!`/`??!` runs, sentence-initial capitals, `i` → `I`, canonical emoticons (`:-)`/`=)` → `:)`), strip emoji skin-tone modifiers.
- **STY-3 Standard level**: + UK→US spelling, common-misspelling correction (misspellings that are real words, like *its*/*cant*/*were*, are left alone), contraction policy, transition normalisation (`furthermore`/`moreover` → `also`; `thus`/`hence` → `so`), intensifier normalisation (`extremely`/`incredibly` → `very`), laughter (`lol`/`hahaha` → `haha`), dash normalisation.
  - Contractions: every variant is first expanded to one long form, then the policy (*contract*, default, or *expand*) is applied. There is no "keep as typed" option (INV-5). Pronoun+be/will contract only when another word follows (`that is, …` stays). `'ve`, `'d` and `'s`-meaning-*has* always stay expanded (*I have a dog* ↛ *I've a dog*).
- **STY-4 Strong level**: + semicolons, exclamation marks and ellipses → full stops (`?` is kept), filler removal (`basically`, `literally`, `honestly`…), chat abbreviation expansion (`u`, `pls`, `bc`, `gonna`…).
- **STY-5 Optional synonym resampling** ("entropy" slider 0–100 %): curated synonym sets only (no word embeddings: nearest neighbours are often antonyms). A resampled word is picked **uniformly from the whole set including the original** (INV-5). Only 100 % fully erases the choice. Default 0 %.
- **STY-6** Output includes changed spans so the UI can show raw vs normalised side by side. The user always reviews before emitting in Full Cloak.
- **STY-7** English only. Other languages pass through unchanged. Detection: texts of 6+ words need ≥ 15 % of words from English function-word/lexicon lists; shorter texts are assumed English.
- **STY-8 Budget**: < 50 ms for 500 words on a mid-range phone; lexicons < 2 MB.
- **STY-9 Style profile** (per mask, sampled from priors; "Uniform" is one fixed profile): spelling region (US/UK), contractions (contract/expand), laugh form (`haha`/`lol`/`hah`/`😂`), emoticon form (`:)`/`:-)`), ellipsis (`...`/`…`), clause dash (` - `/` — `/` – `), sentence case vs lowercase sentence starts (common words only; proper nouns untouched), transition register (plain `also`/`so` vs formal `additionally`/`therefore`), intensifier (`very`/`really`), `OK` form (`OK`/`ok`/`okay`), exclamation (keep single `!` vs full stop), final full stop on the last sentence (keep/omit). The priors are documented estimates in `StyleProfile`, not yet calibrated against public corpora.

## Synthetic playback (TIM)

Soft keyboards don't expose key hold (dwell) time to web pages: IME commits arrive as synthetic
`keydown`/`keyup` pairs (keyCode 229). The leak is the **gap between characters** (flight time).
Typical mobile typing speed is about 36 WPM.

- **TIM-1 Persona**: comes from the active mask (MASK-1), so it is stable per app/identity; a rhythm that changes every session is itself anomalous and can trip bank fraud checks. Every parameter is drawn from population ranges: speed 25–55 WPM, gap spread (log-normal σ 0.25–0.45), pauses, gap floor, burst and typo rates, Typed cap. Never derived from the user (INV-6).
- **TIM-2 Character gaps**: log-normal; median scales with key distance on the QWERTY layout (Fitts-style), plus word-boundary and sentence-boundary pauses and rare long "thinking" pauses. Minimum gap drawn per persona (20–45 ms); no fixed constants that could mark the tool.
- **TIM-3 Playback styles**: *Typed* (one commit per character, TIM-2 timing); *Glide* (one commit per word, mimicking swipe typing; fewer events, faster); *Instant* (a single commit).
- **TIM-4** If a *Typed* plan would exceed the persona's cap (35–60 s), fall back to *Glide* and tell the user.
- **TIM-5** Plans are resumable: for any number of executed steps the plan reports the correctly committed prefix and any uncorrected typo characters. On user *Stop* the emitter deletes the stray characters first; the unsent rest returns to the buffer (INV-7). If the field changes mid-typo the stray characters stay in the abandoned field (known limit).
- **TIM-6** Newlines are replaced with spaces for single-line fields.
- **TIM-7 Bursts**: common English digraphs (*th*, *he*, *in*, *er*…) are typed faster by a per-persona factor.
- **TIM-8 Corrections** (*Typed* only): at the persona's typo rate (0.5–3 % of words) playback commits a neighbouring-key letter, sometimes one more letter, pauses as if noticing, backspaces and retypes. The final text is always exactly the intended text.

## Modes and field policy (POL)

| Mode | Buffer | Normaliser | Playback | Default use |
| :--- | :---: | :---: | :---: | :--- |
| **Full Cloak** | on | on | Typed/Glide | Long-form posts, anonymous writing |
| **Biometric Shield** | on | off | Typed/Glide | Messages where your tone must stay exactly yours |
| **Draft Guard** | on | off | Instant | Search bars, URL bars, passwords, short forms |
| **Passthrough** | off | off | live | Games, terminals, numeric pads |

- **POL-1** The user picks a default mode; each field imposes a **ceiling** and the effective mode is the more restrictive of the two.
- **POL-2** Passwords, URIs, emails, person names, postal addresses → at most **Draft Guard** (never rewritten, never paced: a single commit reveals nothing).
- **POL-3** Search / URL / `IME_ACTION_SEARCH|GO` fields default to **Draft Guard**, not Passthrough: autocomplete sends every keystroke to a server. Emit offers **Emit & Go** (commit + editor action).
- **POL-4** Number, phone, date/time classes and `TYPE_NULL` → **Passthrough**.
- **POL-5** One-tap emergency bypass to Passthrough; it resets at the next field.

Why: rewriting or pacing a password or number gains nothing and risks breaking it, while search
and URL fields leak every keystroke to autocomplete servers, so they still need the buffer.

## Clipboard (CLIP)

- **CLIP-1 Private clipboard.** Copy/cut inside the draft goes to ChamBoard's own clipboard: memory only, never the system clipboard, a few items, each expiring after a TTL (toggle; default 10 min), wiped on screen-off. Other apps can't read it.
- **CLIP-2 System paste.** A paste key reads the system clipboard into the draft **only when tapped** (toggle; default on). Pasted text can then be emitted through the normal mode (styled and paced).
- **CLIP-3 Export.** "Copy to system clipboard" is a separate explicit action (toggle; default on). The clip is marked sensitive (hidden from previews and clipboard history on Android 13+) and cleared after a TTL (default 60 s) if it is still ours.

## Known limits

- **Motion sensors**: web pages can read the accelerometer/gyroscope while you type, even into the staging buffer, and tap timing can leak through them. Mitigation is outside the keyboard: block "Motion sensors" in browser site settings.
- **Memory**: the JVM and the target app may keep copies of text; wiping is best-effort.
- **What you send is sent**: the receiving app sees the final text.
- **Detectability**: a site may notice text arriving in whole words or in one burst. Glide mimics swipe typing, which is common. Masks and persona-sampled timing avoid one tool-wide signature, but a classifier trained on ChamBoard output may still spot it. The [adversarial test page](testing.md#adversarial-test-page) measures this, and on simulated sessions its classifier does detect ChamBoard playback: playback commits text without the on-screen word composition (composing-text events) that most phone keyboards produce. This has not yet been measured on a real phone.
- **Typo leftovers**: if the field changes in the middle of a simulated typo, the stray characters stay in the abandoned field (TIM-5).
- **Stylometry is probabilistic**: normalisation shrinks the signal; it doesn't erase topic, opinions, or knowledge that identify you.
