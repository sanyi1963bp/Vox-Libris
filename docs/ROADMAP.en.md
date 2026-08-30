# Vox Libris — development roadmap

🇬🇧 English (this page) · 🇭🇺 [Magyar](TERV.md) · ⬅ [Back to the main page](../README.en.md)

This page records what we are building, in what order, and **why that way**.
Finished parts stay listed so the reasoning behind each decision remains
traceable.

---

## The principle that decides everything

**The app has no internet permission.** That is deliberate: it is technically
incapable of sending data anywhere, and the README says so. For every planned
feature the first question is whether it can be done locally. What cannot is
either solved by a detour or deliberately postponed.

The second principle: **the app builds its own catalogue from the books on the
phone.** No external database, no server, no accounts — it works the same for
anyone who installs it.

---

## Phase 1 — Cleanup and foundations ✅ *(done)*

- **External catalogue removed.** It used to be possible to load a `.db` built
  on a PC. That was tailored to one person's collection, so it is gone: the app
  builds the catalogue itself.
- **Scanning and catalogue building merged.** They used to be two overlapping
  operations; now there is one: *Read the library*.
- **The catalogue stays a visible file**
  (`Download/KonyvtarTTS/sajat_katalogus.db`), so it survives reinstalling the
  app and can be opened on a PC.
- **The shelf became the start screen**, with this startup logic:
  - folder and catalogue exist → straight to the shelf,
  - folder but no catalogue → it offers to read the library,
  - nothing set up → it asks for the folder first, then offers the scan.
- **Reading counters** on top of the shelf (finished / in progress); tapping
  one opens the list.
- **Progress bar** in the reader's book info dialog (it was missing).

## Phase 2 — Covers and the full shelf

- **Cover extraction**: EPUB (OPF `cover`), MOBI/AZW3 (EXTH 201), FB2
  (`<binary>` base64), PDF (render the first page). Where no cover exists the
  current **typographic cover** stays.
- **Thumbnail store**: downscaled (about 320×480 WebP, ~20 KB each), with the
  size shown in settings and a clear button.
- **Two passes**: metadata runs through quickly first (the shelf is usable
  right away), then real covers load in the background.
- **Covers in the file list** as well — behind a **toggle**, off by default,
  because taller rows fit fewer books on screen.

## Phase 3 — Reading experience

- **Bionic Reading**: the first ~40% of every word in bold, toggleable.
- **Long press → action menu** instead of the current instant bookmark:
  *Bookmark · Wikipedia · Quote card · Copy*.
- **Wikipedia**: hands the selected word to the browser — so the app still
  **needs no internet permission**.
- **Quote card**: an image made from the selected text, in the current colour
  scheme, ready to share.

## Phase 4 — Knowing the book

- **Character index**: collects capitalised names from the part you have read
  that do not only appear at sentence starts (built on the existing sentence
  boundary detector). Ranked by frequency, each with the sentence of its first
  appearance. Spoiler-free by construction, since only the read part is
  scanned. Hungarian inflection is handled by stem folding — imperfect, but it
  works for the main characters.
- **"Where was I?"**: sentences of the last chapter are scored by the
  chapter's most frequent content words, and the 3–4 most characteristic ones
  are shown in their original order. It does not retell the story, it **puts
  you back into the text** — and needs no AI at all.

## Phase 5 — Statistics

- Logging of reading sessions, giving **reading speed (WPM)**, **time left in
  the chapter** (from the remaining characters and the speech rate), and a
  **heatmap** of when you read the most.

---

## Deliberately not built (for now)

| Idea | Why not now |
|---|---|
| Cloud AI summaries | API key, cost, and the "no internet" guarantee would be lost. The extractive summary (phase 4) is the local answer. |
| Community margin notes | Server, accounts, moderation — that is a second product. |
| Silent book club | Same: real-time server and moderation. |
| Book lending | Server plus delicate copyright questions. |
| Streaks and badges | Cheap to build, but not what is missing most; if added, behind a toggle. |

## Ideas that came up along the way

- **Pronunciation dictionary**: per-book or global substitutions fed to the TTS
  (`Bree → Bree` style), because synthetic voices routinely mangle invented
  names. Entirely local, small effort, a big difference over long listening.
- **Line spacing and margins** in the reader, next to the font size.
- **Paged mode** as an alternative to the current continuous scrolling.
