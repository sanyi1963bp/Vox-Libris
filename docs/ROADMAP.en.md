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

## Phase 1 addendum — the list became the start screen ✅ *(done)*

The shelf failed its test: **with 3500 books, flipping through covers is
hopeless**. The start screen is now the **list**, and everything on it serves
finding a book:

- **Search** across the **title, the author and the file name** at once,
  accent-insensitive. Filtering runs in memory, so it updates as you type.
- **Letter bar** under the search box: one tap on an initial keeps only those
  books. The bar **shows only letters that actually have books** — no buttons
  leading nowhere. It follows the sort order (title or author), and accented
  letters fold into their base letter.
- **A format badge on every row** (EPUB, PDF, MOBI…), colour-coded. Formats we
  cannot extract text from are **grey**, so the list itself tells you which
  books will speak.
- **Format filter** next to it: lists what is on the phone with counts
  (EPUB 2100, PDF 900…) and narrows the list with one tap.
- **Taps**: one = select, **two = open and read aloud**,
  **long press = details**. The details sheet has the cover on top.
- **The details sheet says what to expect** from that format: whether chapters
  are accurate, or that a PDF layout may disturb the narration.

The **shelf is still there**, one tap away in the top bar — and it shows
whatever the list currently shows, so a search or a letter narrows the shelf too.

## Cleanup ✅ *(done)*

Not a feature — putting the code in order, done here because the coming
phases land exactly in the messiest parts.

- **A safety net: 32 unit tests.** There were none. They cover the parsers
  and the text handling — where a bug is most dangerous, because nothing
  crashes, a book is just read out wrong. For this the parsers use a standard
  XML reader instead of `android.util.Xml`, so they run without an emulator.
  Run them with `gradlew testDebugUnitTest`.
- **One details sheet instead of three.** It lived in three identical copies,
  which is why an earlier fix landed in only two of them.
- **The reader split up.** The 840-line composable became five files: state,
  top bar, controls, text, bookmarks.
- **The view model split in two**: the catalogue and the file browser.
- **The nine settings cards** are separate composables with their own state.

Three real bugs surfaced along the way, all fixed: the file browser opened
empty after a cold start; the browser's current folder also counted as the
library root; and the `&Otilde;` / `&odblac;` entities were not decoded.

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
