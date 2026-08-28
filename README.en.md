# Vox Libris

**Local e-book library browser and text-to-speech reader for Android — built for very large libraries.**

🇬🇧 English (this page) · 🇭🇺 [Magyar](README.md)

🔧 [Setup & dependencies](docs/SETUP.en.md) · 📖 [User guide](docs/USAGE.en.md) · 📝 [Changelog](CHANGELOG.md)

---

A native Android application (Kotlin + Jetpack Compose) that matches the book
files on your device against a local SQLite catalogue (~68,000 book records),
and reads them aloud with the system TTS engine — sentence by sentence, with
automatic resume, bookmarks and headset button control.

Cover images are **deliberately never touched**: the app reads no image from
external folders, nor from inside the book files. That is what keeps scrolling
instant with 70,000+ records, and keeps memory usage flat.

> **Note:** the user interface is in Hungarian. The app itself is
> language-agnostic (any SQLite catalogue and any supported book format works),
> but the labels, dialogs and the sentence-splitting heuristics are tuned for
> Hungarian.

The app appears on the phone as **Könyvtár TTS** ("Library TTS").

## Gestures at a glance

| Gesture | In the file browser | In the reader |
|---|---|---|
| **Single tap** | open folder / show book details | — |
| **Double tap** | read aloud from the last position | read aloud **from the tapped sentence** |
| **Long press** | open the reader | add a bookmark to the paragraph |

## Features

- **Catalogue integration.** Metadata from the `konyvek` (books) table —
  title, author, synopsis, publisher, tags — plus reuse of the file↔book
  matching already done on the PC and stored in `fizikai_fajlok`
  (physical files), so the phone barely has to work.
- **Build a catalogue from scratch.** With no prepared database, the app can
  create one **from the books' own embedded metadata** (EPUB/FB2/MOBI/DOCX
  headers: title, author, synopsis, publisher, series, ISBN, tags) — with no
  internet. Re-running it is **incremental**: existing entries are left
  untouched, only newly copied books are added.
- **Total Commander style browser.** Dense, icon-free rows (name, size, date,
  matched author/title), folder navigation, recursive scanning, sort by
  tapping a column header, fast-scroll bar, storage switcher
  (internal ⇄ SD card ⇄ USB).
- **Unified reader + player screen.** The text and every playback control in
  one place. Bottom bar: chapter ⏫⏬, page-by-screen ⬆⬇, sentence ⏮⏭,
  play/pause. Next to it: font size, position slider, **follow mode** (the
  text scrolls along with the narration) and **tuning** (speed, pitch).
  Top bar: search, bookmark, bookmark list, stop.
- **Sentence-level narration.** TTS advances sentence by sentence, the
  current sentence is highlighted, and the saved position is
  sentence-accurate — so resuming always picks up exactly where you stopped.
- **In-text search.** Accent-insensitive (typing *varazslono* finds
  *varázslónő*), with highlighted hits, ▲▼ navigation and a match counter.
- **Bookmarks.** Long-press anywhere; list, jump, delete. A bookmark added
  during playback lands on the sentence being read.
- **Headset buttons** (Bluetooth and wired, via MediaSession):
  1 press = play/pause, 2 presses = rewind ~5 seconds.
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

## Expected database

The app opens an arbitrary SQLite file **read-only**. The minimum it needs:

```sql
CREATE TABLE konyvek (            -- books
    id           INTEGER PRIMARY KEY,
    szerzo       TEXT,            -- author
    cim          TEXT,            -- title
    leiras       TEXT,            -- synopsis / blurb
    kiado        TEXT,            -- publisher
    kiadas_eve   TEXT,            -- year
    isbn         TEXT,
    sorozat      TEXT,            -- series
    sorozat_szama TEXT,           -- series index
    cimkek       TEXT,            -- tags
    formatum     TEXT,            -- format
    meret        TEXT,            -- size
    ncore_id     TEXT,
    feltoltve_datum TEXT          -- upload date
);

-- Optional, but makes matching dramatically faster:
-- filename → book assignment already computed on the PC
CREATE TABLE fizikai_fajlok (     -- physical files
    fajl_nev  TEXT,               -- file name
    konyv_id  INTEGER REFERENCES konyvek(id)
);
```

If `fizikai_fajlok` is missing or empty, the app falls back to matching
title + author parsed from the file name, using accent- and
punctuation-insensitive normalisation.

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
2. Copy the catalogue `.db` file to the phone — **the `.db` only**, not the
   `-wal` and `-shm` files. Recommended location: the root of internal
   storage or the `Download` folder (the app finds it there automatically
   under the name `ncore_konyvtar.db`); from anywhere else pick it under
   Settings → "Adatbázisfájl kiválasztása…" (select database file).
   Keep it on internal storage rather than an SD card: SQLite performs many
   small random reads, which are considerably faster there.
3. Copy your book files to any folder (these are fine on an SD card).
4. On first launch grant the **"All files access"** permission (the button
   takes you to system settings), and the notification permission on
   Android 13+.
5. Navigate to your books folder and press the **radar icon** — this scans
   everything recursively and matches it against the catalogue. Afterwards
   the **"Katalógus"** (catalogue) tab shows every file found in one
   searchable list.

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
