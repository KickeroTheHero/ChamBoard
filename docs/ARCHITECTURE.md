# ChamBoard architecture

A readable overview. The detailed spec, with requirement IDs, is in [`design/`](design/).

## What problem it solves

When you type into an app or a web page, three things about you leak besides the words you finally
send. The page can watch your **drafts** as you type and delete them. It can time the **gaps between
your keystrokes**, which are as personal as a signature. And it can study your **writing habits**
(spelling, punctuation, contractions, favourite words) to link posts written under different names.

ChamBoard is an Android keyboard that tackles all three at the source, before anything reaches
the app.

## The two halves

```mermaid
flowchart TB
  subgraph app["app — the Android keyboard shell (thin)"]
    svc[ChamImeService] --> views[KeyboardView · DraftPanel · ControlBar · SuggestionStrip]
    svc --> emitter[Emitter]
    emitter -->|commitText| field[(the app's text box)]
  end
  subgraph core["core — the engine (pure Kotlin, no dependencies)"]
    buffer[Staging buffer]
    mask[Masks]
    policy[Field policy]
    style[Style engine]
    timing[Playback planner]
  end
  svc -->|asks| core
  core -->|a plan of what to type and when| emitter
```

- **The engine (`core`)** makes every decision: what text goes out, in which mode, rewritten how, and
  with what timing. It is plain Kotlin with no Android code and no libraries, so it can be tested
  quickly on any computer and audited on its own.
- **The shell (`app`)** draws the keys, forwards your taps to the engine, and carries out the
  engine's plan by typing into the app's text box. It deliberately contains as little logic as
  possible.
- **Physical keyboards** type into the private draft too, and Ctrl+Enter sends it. In
  unprotected boxes, keys go straight through.
- **Screen readers:** every key is exposed to TalkBack with a spoken name, and you can explore
  the keys by touch.
- **Word suggestions** come from a fixed built-in list of about 1,100 words. Nothing is learned.

## How each leak is handled

**Drafts.** While a protected mode is on, your typing goes into a private notepad inside the
keyboard. The app sees nothing until you press **Emit**. The notepad is wiped when the screen turns
off, when you switch text boxes (after being parked for a short grace period), after sending, and
when the keyboard has been hidden too long. The notepad is read in place rather than copied, so a
key press leaves one temporary copy in memory (the one the screen needs) instead of several.

**Rhythm.** When you emit, the text is typed in by a made-up typist with a realistic phone speed
(25–55 words per minute), natural pauses, faster common letter pairs and the occasional corrected
typo. Nothing about the timing comes from you.

**Style.** Identifying habits are rewritten into those of a *mask*. There are three strengths, from
tidying spacing and capitals up to flattening exclamation marks and chat shorthand. Passwords, web
addresses, emails, numbers and names are never rewritten.

## Masks: why not one shared style?

The first design gave everyone the same tidy house style. While few people use the keyboard, that
style itself would give users away: "writes like ChamBoard". So each app, or each named
identity you create, gets its own **mask**: a made-up writer and typist that stays the same over
time, like a real person would, and can be swapped on demand. Masks are calculated from a secret on
the device rather than stored, so the settings never list which apps you used.

## Modes

| Mode | What it does | Typical use |
|---|---|---|
| Full Cloak | private draft, style rewritten, rhythm disguised | anonymous posts |
| Biometric Shield | private draft, rhythm disguised, your words kept exactly | personal messages |
| Draft Guard | private draft, sent in one go | search bars, web addresses, passwords |
| Passthrough | normal keyboard | games, number pads |

Each text box sets a ceiling: a password box can never be rewritten, and a search box gets Draft
Guard by default because search suggestions send every keystroke to a server.

## The promises that are never broken

The keyboard has no internet permission and no permissions at all. It never saves, logs or sends
what you type. The system clipboard is touched only when you tap for it. These promises are checked
by tests and build checks: the build fails if any permission appears in the manifest, and CI
fails if the app logs or touches the clipboard outside one audited file.

## What it can't protect you from

Motion sensors can still leak tap timing to a web page; memory copies can outlive a wipe; whatever
you send is sent; a determined classifier might spot the tool; and rewriting habits doesn't hide
what you write about. The plain-language guide spells these out (`docs/guide/limits.md`).

## How it is built and checked

The engine builds and tests anywhere with a JDK (`scripts/check`). The Android app is built with
the Android SDK, locally or in GitHub Actions, which also runs Android's own lint, checks that no
permission sneaks in, and renders pictures of the real keyboard on a phone-sized emulator. Without
an SDK, `scripts/check` still compile-checks the app code against a copy of Android's API
(`tools/appcheck/`) when one is available.

## Releases and signing

Every Android app carries a digital signature, and a phone only accepts an update signed with the
same key as the version it has. Release builds are meant to be signed in CI from a key stored as a
repository secret. F-Droid signs its own copies from the source code.

## How the spy test page works

To show the protections actually work, a small web page in `adversary/` plays the attacker. It
records every keystroke event and draft change the way a tracking script would, then tries to
recognise the typist by rhythm and by writing habits, and to tell a human from ChamBoard. It
runs only on the tester's own computer: plain HTML and JavaScript, no packages, nothing sent
anywhere.

On simulated typing, rhythm and style give people away without ChamBoard, and with it the
rhythm detector sees only the mask's rhythm. But the "human or ChamBoard?" check can spot the
keyboard, because it sends words without the "being built on screen" step most phone keyboards
show. That is listed as a known limit.
