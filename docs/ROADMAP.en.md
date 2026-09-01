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

## Phase 2 — Covers and the now-playing bar ✅ *(done)*

- **Cover extraction** from the book files themselves. Every format hides it
  somewhere else: **EPUB** marks it in the OPF (in three different ways:
  `<meta name="cover">`, `properties="cover-image"`, or an image named
  "cover"), **MOBI/AZW3** keeps the image's record number in an EXTH record,
  **FB2** embeds it as base64, and for **PDF** the first page is rendered.
  Where there is no cover, the drawn one stays.
- **Thumbnail store**: downscaled WebP (320×480, ~20 KB each) in the app's own
  folder. Its size is shown in settings and it can be cleared — it is not
  precious, it can always be extracted again.
- **Two passes**: metadata runs through quickly and the library is usable at
  once, then covers load in the background. What was extracted once is never
  attempted again.
- **Covers in the list** behind a toggle, off by default.
- **A now-playing bar** at the bottom of every screen: you see which book is
  playing and how far it is, one tap takes you back to it, and the button
  silences it from anywhere. It only appears when a book is loaded.

Cover extraction is guarded by **16 new unit tests** (including the MOBI
byte-exact offset arithmetic), for 48 tests in total.

## File operations and personal notes ✅ *(done)*

**Rename, move, copy, delete** — the same menu from every view.

The point is not moving files; a file manager does that too. The point is
that **everything attached to the file follows it**: the catalogue entry, the
reading progress, the bookmarks, the note and the thumbnail. Doing the same
in a file manager would silently lose all of it, and the book would come back
as new, from zero.

- **Deleting always asks first** and shows the file name — it is the one
  irreversible operation.
- **No file operations in the reader**: the book you are reading will not be
  renamed or deleted from under you. You can still write a note there.
- A copy becomes another file of the same work in the catalogue — the schema
  has always allowed several files per book.

**Personal notes**: attach anything to a book. A small mark in the list shows
which books have one, and **search looks inside the notes too**.

## Swiping between the views ✅ *(done)*

The **library** and the **file browser** became one swipeable surface: swipe
left or right to switch. Two dots next to the title show where you are —
without them the gesture would be invisible.

System back goes from the second page to the first instead of leaving the app.

**The shelf stays out of the swipe**, deliberately: there a swipe already
means moving between books, and the two gestures would fight each other.

**The context menu is the same in both views**: it opens on long press, and
its first entry is the **details sheet**, followed by the note and the file
operations.

## Phase 3 — Reading experience ✅ *(done)*

- **An action menu on the text**: *Bookmark · Pronunciation · Wikipedia ·
  Quote card · Copy*. The menu works on the **sentence you touched**, and
  shows it, so there is no guessing what the action applies to. The sentence
  is the right unit because it is what the narrator works with too: what the
  menu shows is exactly what you hear.
- **A pronunciation dictionary**, which started as a stray idea at the bottom
  of this page and ended up the most valuable part of the phase. You hear the
  voice mangle a name → long press → *Pronunciation* → type how it should
  sound → **every book says it properly from then on**. If narration is
  running, the sentence is re-spoken straight away with the fix.
- **Wikipedia**: hands the word to the browser, so the app still **has no
  internet permission** — it downloads nothing, it only asks the system to
  open an address. The article language follows the interface language.
- **Quote card**: an image made from the sentence in the running colour
  scheme, ready to share. It goes to the cache, and FileProvider hands it on.
- **Bionic Reading**: the first ~40% of every word in bold, toggleable. The
  switch sits in the reader's tuning row next to the font size — where you
  can see at once what it does to the text.

### Where we departed from the plan, and why

- **No text selection.** The plan said "the selected word", but Compose text
  selection would fight the double tap (which starts narration), and at a
  large font size it is a struggle one-handed anyway. Instead the menu
  **offers the sentence's words as chips**: tap the name and you are done.
  That is also how a pronunciation rule gets entered.
- **The long press is configurable.** The plan simply had the menu take the
  long press away from bookmarking. But bookmarking is the most frequent
  action in the text, and that would have cost a tap. So it is a **setting**:
  by default long press opens the menu; switched over, long press bookmarks
  and a single tap opens the menu. Both stay available, the two gestures just
  swap.
- **The pronunciation dictionary is global**, not per book. Mispronounced
  names tend to recur across a series and across files, so they are worth
  entering once. The substitution is **anchored to the start of a word but
  leaves the ending alone** (`Bree` also fixes "Breeben"), because in
  Hungarian the suffix is at the end and the stem is at the front.
- **The substitution only touches the text handed to the engine**, never the
  book's text. That is not cosmetic: it keeps the character positions of the
  highlighted sentence from shifting, and search keeps searching the original.

**24 new unit tests** cover this phase (pronunciation, sentence bounds, word
picking, bionic weighting), bringing the total to 79.

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

- ~~**Pronunciation dictionary**~~ — built in phase 3, globally.
- **Line spacing and margins** in the reader, next to the font size.
- **Paged mode** as an alternative to the current continuous scrolling.
