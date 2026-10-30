# ChamBoard roadmap

## Where it's going

An open-source Android keyboard with no permissions that hides drafts and disguises writing style
and typing rhythm for each identity, published as signed builds on GitHub and F-Droid.

## Done

| Stage | What it delivered |
|---|---|
| 1. Groundwork | The specification, automatic checks, the plain-language guide |
| 2. The engine | Private notepad, text-box rules, rhythm disguise, three style strengths, masks, private clipboard and friction switches |
| 3. The keyboard | An installable test version with private drafts, rewrite preview, disguised typing, per-box rules and CI screenshots |

## Now: hardening

Closing the known gaps of the test version so it is safe and usable enough to release: word
suggestions from a fixed list, physical-keyboard support, screen-reader support, the mask secret in
Android's key store, fewer copies of drafts in memory, UI polish, and a local "spy" test page that
tries to identify the typist. Most of this is built; a with-and-without comparison on a real phone
is still to do.

## Next: release

Signed builds from CI, an F-Droid listing with reproducible builds, and real screenshots in the
guide.
