# ChamBoard

ChamBoard is a keyboard for Android that stops apps and websites from recognising you by
*how* you type. It keeps drafts private until you send them, rewrites identifying writing habits
into those of a made-up "mask", and types your text in at a realistic rhythm that isn't yours. It
has no internet permission.

> **Status:** early test version. There's no app-store release yet; see
> [Installing it](docs/guide/install.md).

When you type online, you leak more than your words: the drafts you delete, your personal typing
rhythm, and your writing habits. Tracking and fraud-detection companies use all three to recognise
people across accounts. ChamBoard:

- 📝 **Hides your drafts.** You type into a private notepad. Websites see nothing until you press Emit.
- ⏱️ **Disguises your rhythm.** Your text is typed in at a random, realistic human pace that isn't yours.
- 🎭 **Masks your writing style.** Identifying habits (typos, punctuation, spelling) are rewritten
  into those of a *mask*: a made-up, realistic writer that stays the same per app, so your accounts
  don't look alike. You review the result before sending.
- 🔒 **Can't go online.** The app declares no permissions at all, and the build fails if one appears.

## For everyone

Start with the [plain-language guide](docs/guide/README.md): what it does, the four modes, what it
*can't* protect you from, [how to install it](docs/guide/install.md), and
[what's built so far](docs/guide/status.md).

## For developers

| You want… | Go to |
|---|---|
| to build, test and contribute | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| the architecture in plain prose | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| the detailed spec and requirement IDs (INV, BUF, MASK, STY, TIM, POL, CLIP, UI) | [`docs/design/`](docs/design/) |
| code style and layout | [`docs/STYLE.md`](docs/STYLE.md) |
| where the project is heading | [`docs/ROADMAP.md`](docs/ROADMAP.md) |
| the "spy" test page that plays a tracker | [`adversary/`](adversary/) (open `index.html`) |

Quick start: `scripts/check` runs formatting, detekt, the engine tests, the privacy invariant checks
and the test-page tests. With an Android SDK, `./gradlew :app:assembleDebug` builds the APK.

## Jargon

| Term | Meaning |
|---|---|
| IME | Input method editor: an Android keyboard app. |
| Draft / staging buffer | The private notepad inside the keyboard; nothing reaches the app until Emit. |
| Mask | A made-up writer and typist, stable per app or named identity, that your text is rewritten and replayed as. |
| Stylometry | Recognising an author from writing habits. |
| Keystroke dynamics | Recognising a person from the timing between key presses. |
| Mode | Full Cloak, Biometric Shield, Draft Guard or Passthrough: how much protection a text box gets. |

## Licence

Public domain, under [The Unlicense](LICENSE). Do whatever you like with it.
