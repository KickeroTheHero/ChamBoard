# What's built so far

ChamBoard is an early **test version**. Everything below works and is covered by automatic
checks, but it hasn't been through a public release yet.

## The engine

The keyboard's "brain" lives in `core/` and has no Android code, so it can be tested on any computer.

- **Private notepad:** holds your draft and scrubs it from memory when it's cleared.
- **Text-box rules:** the keyboard recognises password, email, web-address, search and number boxes
  and picks the right protection for each. Passwords and addresses are never rewritten. Search
  boxes keep your typing private until you press Emit.
- **Rhythm disguise:** text is typed in by a made-up typist with a realistic phone typing speed
  (25–55 words per minute), natural pauses, quick bursts on common letter pairs and the occasional
  typo that gets noticed and fixed. The final text is always exactly what you approved. Long
  messages switch to word-by-word so they don't take minutes.
- **Style tidying** comes in three strengths:
  - **Light:** spacing, quotes, capital letters and smileys.
  - **Standard:** also spelling, common typos, "don't"/"do not", and words like "furthermore".
  - **Strong:** also chat shorthand, filler words, exclamation marks and semicolons.
- **Masks:** each app gets its own made-up writer and typist, stable over time and swappable on
  demand. Named masks let you keep separate identities apart. In a test, 40 random masks turned the
  same message into over 25 different versions, so there's no single "ChamBoard look".
- **Private clipboard** that other apps can't read and that empties itself.
- **Word suggestions** from a fixed built-in list of about 1,100 everyday English words. Nothing is
  learned or stored, so everyone sees the same suggestions for the same letters.

## The keyboard

See [Installing it](install.md).

- **Private drafts:** typing stays in the keyboard until you press EMIT. In search boxes, the
  button says **GO** and searches in one tap.
- **Rewriting before sending:** in Full Cloak, a "Sends:" line shows exactly what the app will get,
  with the changes highlighted.
- **Disguised typing:** the text is typed in at your mask's pace. **STOP** keeps whatever hasn't
  been sent.
- **Sensible per box:** passwords, email and web addresses are never rewritten; number pads work
  normally.
- **Drafts stay put:** if you switch to another box, your draft waits for the box you wrote it in,
  for as long as the "keep draft" setting allows. It never shows up in a different box.
- **Suggestion strip:** up to three words above the keys while you type in a private draft. Tapping
  one only changes your draft, never the app.
- **Physical keyboards:** a Bluetooth or USB keyboard types into the private draft too, and
  Ctrl+Enter sends it. In boxes that need no protection, keys go straight through.
- **Screen readers:** with TalkBack on, every key is read out by name and you can explore the keys
  by touch.
- **Safer mask secret:** the key that makes your masks is held in the phone's secure key store,
  where even ChamBoard can't read it out.
- **Screenshots and screen recordings** don't capture the keyboard.
- **Friction switches:** keep your draft for a minute when you switch apps, paste from the normal
  clipboard, send with Enter, and word suggestions. Each one can be turned off.

## The "spy" test page

A small web page in the `adversary/` folder plays the part of a website that watches how you type.
Open `adversary/index.html`, type, and it shows every key press and text change a tracker could
see, with exact timings. You can save a recording as a file. It never sends anything anywhere.

It also runs three detectors like a real tracker would: one that recognises people by typing
rhythm, one by writing style, and one that guesses "human or ChamBoard?". On simulated typing,
rhythm and style give people away without ChamBoard, and with it the rhythm detector sees only
the mask's rhythm. But the guesser can tell ChamBoard is in use, because it sends text without
the "word being built on screen" step most phone keyboards show. See
[What it can't protect you from](limits.md).
