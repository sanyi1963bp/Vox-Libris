# Vox Libris

**Local e-book library browser and text-to-speech reader for Android — built for very large libraries.**

🇬🇧 English (this page) · 🇭🇺 [Magyar](README.md)

**Interface in ten languages**: English · Hungarian · German · French ·
Spanish · Portuguese · Polish · Czech · Slovak · Russian

🔧 [Setup](docs/SETUP.en.md) · 📖 [User guide](docs/USAGE.en.md) · 🗺 [Roadmap](docs/ROADMAP.en.md) · 📝 [Changelog](CHANGELOG.md)

---

A native Android application (Kotlin + Jetpack Compose). It **builds its own
catalogue** from the books on your phone — using the metadata stored inside
the files, with no internet — and then reads them aloud with the system TTS
engine: sentence by sentence, with automatic resume, bookmarks and headset
button control.

No account, no server, no prepared database: install it, show it where your
books are, and that is all.

Cover images are **deliberately never touched**: the app reads no image from
external folders, nor from inside the book files. That is what keeps scrolling
instant with 70,000+ records, and keeps memory usage flat.

> **Note:** the interface is available in ten languages and can be switched
> in the settings, independently of the narration language. The
> sentence-splitting heuristics were tuned for Hungarian but work for other
> Latin-script languages as well.

The app appears on the phone as **Könyvtár TTS** ("Library TTS").

## Gestures at a glance

| Gesture | In the file browser | In the reader |
|---|---|---|
| **Single tap** | open folder / open the book for reading | — |
| **Double tap** | open **and read aloud** from the last position | read aloud **from the tapped sentence** |
| **Long press** | open the book | add a bookmark to the paragraph |

## Features

- **Its own catalogue, without internet.** The app walks your books folder and
  builds a catalogue **from the files' own embedded metadata** (EPUB/FB2/MOBI/
  DOCX headers: title, author, synopsis, publisher, series, ISBN, tags).
  Re-running it is **incremental**: existing entries are left untouched, only
  newly copied books are added. The catalogue is a visible file, so it survives
  reinstalling the app.
- **The shelf is the start screen.** A pageable cover view: browse your books
  like standing in front of a shelf. Under each cover a progress bar shows
  where you are — no bar means you have not started it.
- **Total Commander style browser.** Dense, icon-free file rows (name, size,
  date, matched author/title), folder navigation, recursive scanning, sort by
  tapping a column header, fast-scroll bar, storage switcher
  (internal ⇄ SD card ⇄ USB). Each book has an **ⓘ button** for its details
  and a **progress bar** once you have started it.
- **A single book screen.** The text and every control in one place — no
  separate player or details window. One bottom row: **chapter ◀ ·
  paragraph ◀ · sentence ◀ · ▶/⏸ · sentence ▶ · paragraph ▶ · chapter ▶**.
  Next to it a position slider, font size, **follow mode** (the text scrolls
  along with narration) and **tuning** (speed, pitch). Top bar: search,
  settings, and a menu with bookmarks, book info and stop.
- **Audio cues.** A soft tone before every paragraph and a deeper double tone
  before every chapter — each toggleable, with adjustable volume. Chapter
  boundaries are also marked with a **blood-red band** in the text.
- **Appearance.** Light/dark theme or follow-system, **six colour schemes**
  (classic green, ocean blue, sepia, sunset, night, high contrast), and
  separately adjustable font sizes for the interface and the book.
- **Narration language.** Any language the installed TTS engine offers can be
  selected, and the voice installer opens with a single button.
- **Sentence-level narration.** TTS advances sentence by sentence, the
  current sentence is highlighted, and the saved position is
  sentence-accurate — so resuming always picks up exactly where you stopped.
- **In-text search.** Accent-insensitive (typing *varazslono* finds
  *varázslónő*), with highlighted hits, ▲▼ navigation and a match counter.
- **Bookmarks.** Long-press anywhere; list, jump, delete. A bookmark added
  during playback lands on the sentence being read.
- **Headset buttons** (Bluetooth and wired, via MediaSession):
  1 press = play/pause, 2 presses = rewind ~5 seconds.
- **Ten languages.** The whole interface is translated, and the **interface
  language is set separately from the narration language**. Adding a language
  is a single XML file, no coding involved.
- **Reading list and statistics.** Separate **finished** and **in progress**
  sections, with progress bars, listening time and last-opened dates.
- **Export.** The reading record as CSV plus a copy of the SQLite database
  into `Download/KonyvtarTTS/` (to copy over to a PC), or shared straight to
  e-mail / cloud storage.
- **External TTS.** The extracted plain text can be handed to another
  text-to-speech app via `ACTION_SEND`.

## Format support

| Format | Status |
|---|---|
| epub, txt, fb2, htm/html | full |
| mobi, prc, azw, azw3 | full (PalmDOC decompression; clear error message for DRM and HUFF/CDIC files) |
| rtf | full (including Windows-1250 code page) |
| pdf | text layer extraction (scanned PDFs report an error — run OCR and read the .txt) |
| docx | full |
| doc, djvu | listing and matching only — convert for narration (e.g. with Calibre) |

Every format is handled by a **self-contained, dependency-free parser**; PDF
is the only one using an external library (PDFBox-Android).

## Building

Prerequisites: Android Studio, or just the Android SDK + JDK 17.

```bash
gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (~9 MB, signed with
the debug key so it installs right away). From Android Studio: open the
folder and hit Run ▶.

Minimum Android 11 (API 30), target Android 15 (API 35).
AGP 8.11.1 · Kotlin 2.2.0 · Gradle 8.13 · Compose BOM 2025.01.00

## Install and first run

1. Copy `app-release.apk` to the phone and install it (you may need to allow
   "unknown sources").
2. Copy your book files to any folder (an SD card is fine).
3. On first launch grant the **"All files access"** permission (the button
   takes you to system settings), and the notification permission on
   Android 13+.
4. The app asks **where your books are** — pick the folder.
5. Then it offers to **read them**. One button, and the catalogue is built; at
   the end the shelf opens with your books.

After copying in new books just run the scan again (Settings → Catalogue): it
leaves existing entries untouched and only adds the new ones.

**You also need a TTS engine** with a voice for your language. It is part of
the system, not of this app — installing it, downloading voices and
everything about Bluetooth headsets is covered here:
**[docs/SETUP.en.md](docs/SETUP.en.md)**

Full walkthrough of every screen: [docs/USAGE.en.md](docs/USAGE.en.md)

## Architecture

```
app/src/main/java/hu/konyvtar/tts/
├── MainActivity.kt        – navigation, permission screen
├── App.kt                 – application-level init
├── model/Models.kt        – data classes, progress calculation
├── data/
│   ├── CatalogDb.kt       – the external catalogue (read-only) + CatalogHolder
│   ├── AppDb.kt           – own DB: scan cache, positions, bookmarks
│   ├── Matcher.kt         – file name → book matching, text normalisation
│   ├── FileScanner.kt     – recursive scan, incremental cache
│   ├── Exporter.kt        – CSV + SQLite export into Downloads
│   └── Prefs.kt           – settings
├── reader/                – text extraction per format
│   ├── TextExtractor.kt   – single entry point, chapters, text cache
│   └── Sentences.kt       – Hungarian sentence boundary detection
├── tts/TtsService.kt      – foreground service: TTS, position saving, MediaSession
└── ui/                    – Compose screens (Explorer, Detail, Reader,
                             Stats, Settings, FilePicker)
```

Key design decisions:

- **Reading positions live in the app's own database**, not in the
  catalogue — so refreshing the catalogue (re-copying it from the PC) never
  wipes your progress.
- **Extracted text is cached** (`cacheDir/text/`), which makes every reopen
  instant. Chapter boundaries are stored in a small sidecar file next to it.
- Matching primarily uses `fizikai_fajlok.fajl_nev`, reusing work already
  done on the PC, and falls back to title + author parsed from the file
  name — handling the invisible U+200B characters that appear in titles
  scraped from moly.hu.
- The large catalogue is **never copied into the app**; it is opened in
  place, read-only.
- The browser list sorts and filters **in SQL**, not in memory, which is why
  it stays fast with tens of thousands of rows.

## Privacy

The app **requests no internet access** (there is no `INTERNET` permission in
the manifest), so it is technically incapable of sending data anywhere.
Everything — the catalogue, the books, reading positions, bookmarks — stays
on the phone. No analytics, no ads, no accounts.

## License

MIT — see [LICENSE](LICENSE). Free to use, modify and distribute, as long as
the copyright notice is retained.

## Status

A personal project built for daily use, but usable by anyone with a similar
setup. Issues and ideas are welcome.

## Credits

- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) — PDF text
  layer extraction
- The app uses the system TTS engine (Google Text-to-Speech is recommended
  for Hungarian voices)
