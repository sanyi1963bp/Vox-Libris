# Vox Libris

**Local e-book library and text-to-speech reader for Android — built for very
large libraries.**

🇬🇧 English (this page) · 🇭🇺 [Magyar](README.md)

**The interface in ten languages**: Hungarian · English · German · French ·
Spanish · Portuguese · Polish · Czech · Slovak · Russian

📱 [Download and tester guide](https://sanyi1963bp.github.io/Vox-Libris/docs/) ·
🔧 [Setup](docs/SETUP.en.md) · 📖 [Usage](docs/USAGE.en.md) ·
🗺 [Roadmap](docs/ROADMAP.en.md) · 📝 [Changelog](CHANGELOG.md)

---

A native Android app (Kotlin + Jetpack Compose). It **builds its own
catalogue** from the books on your phone — out of the metadata stored in the
files, with no internet — and then reads them aloud through the system's
text-to-speech engine: sentence by sentence, resuming automatically, with
bookmarks and headset-button control.

No account, no server, no prepared database: install it, show it where your
books are, and that's it.

The app appears on the phone as **Vox Libris** (**Könyvtár TTS** in Hungarian).

## Two views

The start screen is the **library**: all your books in one list, with search, a
letter bar and a format filter. **Swipe** left or right to switch to the
**file browser**, where you see the same books by folder. Two dots next to the
title show which view you are on.

There is also a **shelf** (flipping through covers, from the top bar icon) and
a **reader screen**, where the text and every control live together.

## Gestures at a glance

| Gesture | Library and browser | While reading |
|---|---|---|
| **Single tap** | select / open folder | — |
| **Double tap** | open **and read aloud** from the last position | read aloud **from the sentence you touched** |
| **Long press** | context menu (details, note, file operations) | action menu for the sentence you touched |

In the reader the long press is **configurable**: if you would rather have it
bookmark right away, switch it in the settings, and the action menu then opens
on a single tap.

## Main features

- **Its own catalogue, without internet.** The app walks your books folder and
  builds a catalogue **from the files' own metadata** (title, author,
  description, publisher, series, ISBN, tags from EPUB/FB2/MOBI/DOCX headers).
  Running it again is **incremental**: existing entries are left alone, only
  newly copied books are added. The catalogue is a visible file
  (`Download/KonyvtarTTS/sajat_katalogus.db`), so it survives reinstalling the
  app and can be opened on a PC.
- **Search that actually finds things.** It looks at the **title, the author,
  the file name and your own notes** at once, accent-insensitive. Filtering
  runs in memory, so it updates as you type even with thousands of books.
- **A letter bar** that shows only the initials you actually have books for —
  no buttons leading nowhere. It follows the sort order: title or author.
- **Real covers**, extracted from the book files themselves: EPUB (three
  different OPF conventions), MOBI/AZW3 (EXTH record), FB2 (base64), PDF (the
  first page rendered). Stored downscaled as WebP (~20 KB each), loaded in a
  **second, background pass** so the list is usable straight away. Where a
  book has no cover, one is drawn from the title and the author.
- **Format at a glance.** Every row carries a format badge; formats that
  cannot be read aloud are **grey**. The format filter lists what is on the
  phone, with counts.
- **File operations from every view.** Rename, move, copy, delete — and
  **everything attached to the file follows it**: the catalogue entry, the
  reading progress, the bookmarks, the note and the thumbnail. Doing the same
  in a file manager would silently lose all of it.
- **Personal notes.** Attach anything to a book; a mark in the list shows
  which books have one, and search looks inside the notes too.
- **One single book screen.** Text and every control in one place — no
  separate player or detail window. In one row at the bottom:
  **chapter ◀ · paragraph ◀ · sentence ◀ · ▶/⏸ · sentence ▶ · paragraph ▶ ·
  chapter ▶**. Next to it a position slider, font size, **follow** (the text
  scrolls along with the narration) and **tuning** (speed, pitch).
- **A now-playing bar** at the bottom of every screen: you see which book is
  playing and how far it is, one tap takes you back to it, and the button
  silences it from anywhere.
- **Sentence-level narration.** The engine advances sentence by sentence; the
  current sentence is highlighted; the saved position is sentence-accurate, so
  resuming always picks up exactly where you left off.
- **A cue before each chapter** — a deeper double tone, separately switchable
  with adjustable volume. Chapter breaks are also marked in the text by a
  **blood-red band**.
- **Search inside the text.** Accent-insensitive, hits highlighted, ▲▼ to jump
  between them, with a counter.
- **Bookmarks.** From the text's action menu anywhere; list, jump, delete.
- **A pronunciation dictionary.** Synthetic voices routinely mangle invented
  and foreign names. Long-press a sentence → **Pronunciation** → type how it
  should sound, and **every book says it properly from then on**; if
  narration is running, the sentence is re-spoken straight away. The
  substitution is anchored to the start of a word but leaves the ending
  alone, so a `Bree` rule also catches "Breeben".
- **An action menu on the text**: *Bookmark · Pronunciation · Wikipedia ·
  Quote card · Copy*. It works on the **sentence you touched** and shows it —
  the same chunk the narrator speaks as one unit. **Wikipedia** hands the
  word to the browser, so the app itself still never goes online. The **quote
  card** draws a shareable image from the sentence in the running colour
  scheme.
- **Bionic Reading.** The first ~40% of every word in bold, so the eye can
  catch on word beginnings. Toggleable, from the reader's tuning row.
- **Headset buttons** (Bluetooth and wired, over MediaSession):
  1 press = play/pause, 2 presses = ~5 seconds back.
- **Appearance.** Light/dark theme or follow the system, **six colour
  schemes** (classic green, sea blue, sepia, sunset, night, high contrast),
  and separately adjustable font size for the interface and for the book.
- **Ten languages.** The whole interface is translated, and **the interface
  language is set separately from the narration language**. Adding a new
  language is one XML file, no code.
- **Reading list + statistics.** **Finished** and **in progress** books in
  separate categories, with progress bars, listening time and last date.
- **Export.** The reading record to CSV and an SQLite copy in
  `Download/KonyvtarTTS/`, or shared by e-mail or to the cloud.

## Format support

| Format | Narration | Cover |
|---|---|---|
| epub | full | yes |
| fb2 | full | yes |
| mobi, prc, azw, azw3 | full (PalmDOC decompression; a clear message for DRM and HUFF/CDIC files) | yes |
| pdf | text layer extraction (a clear message for scanned PDFs — after OCR they work as .txt) | first page |
| txt, htm/html | full | no |
| rtf | full (including the Windows-1250 code page) | no |
| docx | full | no |
| doc, djvu | no — needs converting first (e.g. with Calibre) | no |

Every format is handled by a **hand-written, dependency-free reader** (only
PDF uses an external library, PDFBox-Android).

## Installing

The easiest route is the
**[download page](https://sanyi1963bp.github.io/Vox-Libris/docs/)**: open it on
the phone and one button installs the app; everything you need to know is on
that page.

By hand:

1. Download the latest APK from the
   [Releases](https://github.com/sanyi1963bp/Vox-Libris/releases) page and
   install it (you may need to allow "unknown sources").
2. On first start grant **"All files access"** (the button takes you to the
   system settings), and on Android 13+ the notification permission too.
3. The app asks **where your books are** — pick the folder.
4. Then it offers to **read them in**. One button, and the catalogue is built;
   covers load in the background afterwards.

After copying in new books just run the scan again (Settings → Catalogue): it
leaves existing entries alone and only adds the new ones.

**You also need a text-to-speech engine** with a voice for your language. That
is part of the system, not of this app — installing it, downloading voices and
the Bluetooth headset details are described in
**[docs/SETUP.en.md](docs/SETUP.en.md)**.

A detailed walkthrough of every screen: [docs/USAGE.en.md](docs/USAGE.en.md)

## Building

Prerequisite: Android Studio, or just the Android SDK + JDK 17.

```bash
gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (~10 MB, signed with
the debug key so it installs right away). From Android Studio: open the
folder, then Run ▶.

The unit tests run without an emulator:

```bash
gradlew.bat testDebugUnitTest
```

Minimum Android 11 (API 30), target Android 15 (API 35).
AGP 8.11.1 · Kotlin 2.2.0 · Gradle 8.13 · Compose BOM 2025.01.00

## Architecture

```
app/src/main/java/hu/konyvtar/tts/
├── MainActivity.kt            – navigation, the swipeable main screen, permissions
├── App.kt                     – app-level initialisation
├── model/Models.kt            – data classes, progress calculation
├── data/
│   ├── Catalog.kt             – the app's own catalogue (a visible .db in Downloads)
│   ├── AppDb.kt               – positions, bookmarks, notes
│   ├── LibraryScanner.kt      – the scan: metadata extraction + catalogue write
│   ├── MetadataExtractor.kt   – title, author, description from the files
│   ├── CoverExtractor.kt      – cover extraction per format
│   ├── CoverStore.kt          – thumbnail store (WebP + memory cache)
│   ├── CoverScanner.kt        – the covers' background second pass
│   ├── FileOps.kt             – rename, move, copy, delete
│   ├── Pronounce.kt           – pronunciation dictionary for the narrator
│   ├── QuoteCard.kt           – drawing and sharing the quote card
│   ├── Normalizer.kt          – text normalisation, accent folding
│   ├── Exporter.kt            – CSV + SQLite export to Downloads
│   └── Prefs.kt               – settings
├── reader/                    – text extraction per format
│   ├── TextExtractor.kt       – single entry point, chapters, text cache
│   ├── Sentences.kt           – sentence bounds, word picking for the menu
│   ├── Bionic.kt              – which word beginnings get bolded
│   └── XmlReader.kt           – Android-free XML, so the parsers are testable
├── tts/TtsService.kt          – foreground service: TTS, position, MediaSession
├── vm/
│   ├── LibraryViewModel.kt    – catalogue, filtered list, shelf, scanning
│   └── BrowserViewModel.kt    – browsing the file system
└── ui/                        – Compose screens
    ├── LibraryScreen.kt       – the library list (start screen)
    ├── ExplorerScreen.kt      – file browser
    ├── ShelfScreen.kt         – swipeable cover view
    ├── ReaderScreen.kt        – state and wiring; the presentation lives in
    │                            ReaderTopBar, ReaderControls, ReaderText,
    │                            BookmarksDialog
    ├── BookDetails.kt         – the book's details sheet (for all three views)
    ├── FileActions.kt         – the context menu and its dialogs
    ├── ReaderActions.kt       – the text's action menu, word picker, pronunciation
    ├── PronounceCard.kt       – the pronunciation dictionary in the settings
    └── …                      – Settings, Stats, FilePicker, NowPlayingBar

app/src/test/java/hu/konyvtar/tts/   – 79 unit tests, no emulator needed
```

Notable design decisions:

- **Reading positions live in the app's own database**, not in the catalogue —
  so rebuilding the catalogue never destroys your progress.
- The **catalogue is deliberately a visible file** in Downloads rather than in
  the app's private storage: it survives reinstalling, and opens on a PC.
- The **list filters in memory**, not in SQL: the catalogue is loaded once with
  pre-computed, accent-free keys — which is what keeps search instant while
  typing, even with thousands of books.
- **Covers load in a separate pass.** Extracting a cover is far more expensive
  than reading metadata (decoding an image, rendering a PDF page), so the list
  is never made to wait for it.
- **Extracted text is cached** (`cacheDir/text/`), so after the first open,
  resuming is instant. Chapter boundaries are saved in a small sidecar file.
- **File operations carry the references over.** That is their whole point: the
  catalogue, the progress, the bookmarks, the note and the thumbnail all
  follow the file.
- **The parsers do not depend on Android** (a small `XmlReader` of our own), so
  they can be tested with plain JUnit — that is where a bug is most dangerous,
  because nothing crashes, a book is just read out wrong.

## Privacy

The app **does not request internet access** (there is no `INTERNET`
permission in the manifest), so it is technically incapable of sending data
anywhere. Everything — the catalogue, the books, the reading positions, the
bookmarks, the notes — stays on the phone. No analytics, no ads, no user
account.

## Licence

MIT — see the [LICENSE](LICENSE) file. Free to use, modify and distribute,
keeping the copyright notice.

## Status

A personal project, built for daily use, but anyone is welcome to use it for
the same purpose. Bug reports and ideas are welcome in Issues.

## Thanks

- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) — PDF text
  layer extraction
- The app uses the system TTS engine (Google Text-to-Speech is a good choice
  for most languages)
