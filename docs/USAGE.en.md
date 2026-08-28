# Vox Libris — user guide

🇬🇧 English (this page) · 🇭🇺 [Magyar](HASZNALAT.md) · ⬅ [Back to the main page](../README.en.md)

> **Installation**, setting up the TTS engine and voices, Bluetooth details
> and build dependencies live on a separate page:
> **[Setup & dependencies](SETUP.en.md)**

> **Note:** the app's user interface is in Hungarian. Where a button or label
> matters, this guide gives the Hungarian text in quotes with the translation
> next to it.

---

## Contents

1. [What you need](#1-what-you-need)
2. [Installation](#2-installation)
3. [First launch — permissions](#3-first-launch--permissions)
4. [Setting up the catalogue](#4-setting-up-the-catalogue)
5. [File browser](#5-file-browser)
6. [Book details](#6-book-details)
7. [Reader screen](#7-reader-screen)
8. [Headset buttons](#8-headset-buttons)
9. [Reading list and export](#9-reading-list-and-export)
10. [Settings](#10-settings)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. What you need

- A phone running **Android 11 or newer**.
- A **system TTS engine** with a voice for your language. Google
  Text-to-Speech is preinstalled on most phones; the voice data has to be
  downloaded once (Settings → Accessibility → Text-to-speech output).
- **Book files** on the phone or an SD card.
- Optionally an **SQLite catalogue** (`.db`) with the metadata. The app works
  without one too — you simply get no title, author or synopsis next to the
  files.

## 2. Installation

1. Copy `app-release.apk` to the phone.
2. Tap it in a file manager and install. If the system blocks it, allow that
   file manager to install from "unknown sources".
3. To update, install the new APK over the old one — **your data (positions,
   bookmarks, scan cache) is preserved.**

## 3. First launch — permissions

An explanatory screen greets you on first launch:

- **"All files access"** — required so the app can see your books and the
  catalogue anywhere on storage. The button takes you to system settings;
  turn it on and go back to the app (it notices by itself).
- **Notification permission** (Android 13+) — requested in a popup. Granting
  it puts the playback controls in the notification shade and on the lock
  screen.

## 4. Setting up the catalogue

The app automatically looks for `ncore_konyvtar.db` in the root of internal
storage and the SD card, and in the `Download`, `Documents` and `Books`
folders.

If yours lives elsewhere or has another name: **Settings** (gear icon, top
right) → **"Adatbázisfájl kiválasztása…"** (select database file), then browse
to it.

The header always shows the state: `68319 könyv` (books, in green) or
`nincs DB` (no database, in red).

> **Tip:** keep the `.db` on **internal storage**, not on an SD card. SQLite
> does many small random reads, which are noticeably slower on a card. Book
> files are fine on a card — the app reads each one once, after which the
> extracted text comes from the cache.
>
> When you refresh the catalogue on the PC, copy it over the old one with the
> same name and path — everything keeps working and your positions survive.

## 5. File browser

The app's home screen: dense, icon-free rows so that as many entries as
possible fit on screen.

### Two views

- **"Mappák"** (folders) — the real directory structure of your storage.
- **"Katalógus"** (catalogue) — every previously scanned file in **one flat
  list**, regardless of folders. With a large collection this is the more
  useful one: you can search and sort across your whole library.

### Top bar

| Element | What it does |
|---|---|
| 📡 **radar** | recursive scan from the current folder down, plus catalogue matching |
| 📊 **bar chart** | reading list and statistics |
| ⚙ **gear** | settings |
| **search field** | filter by file name, title and author |
| 💾 **SD card icon** | switch storage volume (internal, SD card, USB) |
| ⬆ **arrow** | go up one level |

### Columns and sorting

The header reads **Név · Szerző · Méret · Dátum** (name · author · size ·
date) — tap any of them to sort by it, tap again to reverse. The active
column is coloured and marked with a ▲▼ arrow.

Every row has two lines: file name, size and date on top; below it — when the
catalogue has a match — **the author and title in green**.

### Scanning

The radar icon walks the current folder and all its subfolders, collects book
files and matches them against the catalogue. You can watch the file and
match counts as it goes; "Mégse" (cancel) stops it.

Scanning is **incremental**: unchanged files are not reprocessed, so the
second run is much faster.

### Moving fast

A **fast-scroll bar** sits on the right edge of the list — drag it to fly
through tens of thousands of rows instantly.

## 6. Book details

Opens on a **single tap** on a file. It shows:

- title, author and every catalogue field (publisher, year, ISBN, series,
  tags),
- the **synopsis / blurb**,
- the first few thousand characters of the book as a **preview**,
- your progress, if you have started it.

Buttons: **"Folytatás"** (continue) or **"Felolvasás"** (read aloud),
**"Elölről"** (from the start), **share** (hand the full text to another TTS
app), and **"Olvasás képernyőn"** (read on screen).

## 7. Reader screen

The heart of the app: **you read and listen here** — there is no separate
player screen.

It opens from a long press in the browser, from the details screen, from the
reading list, or by tapping the notification.

### Top bar

| Icon | Function |
|---|---|
| 🔍 | search in the text (accent-insensitive) |
| 🔖+ | add bookmark |
| 🔖 | bookmark list |
| ⏹ | stop narration completely (only while it is playing) |

### Bottom control bar

| ⏫ | ⬆ | ⏮ | ▶/⏸ | ⏭ | ⬇ | ⏬ |
|---|---|---|---|---|---|---|
| previous chapter | one screen back | previous sentence | play/pause | next sentence | one screen forward | next chapter |

Below it:

- **A− / A＋** — font size (remembered),
- **position slider** — jump anywhere in the book, with a percentage readout,
- 🎯 **follow** — when on, the text scrolls along with the narration (it will
  not yank the page away while you are scrolling yourself),
- 🎚 **tuning** — speed (0.5×–3×) and pitch sliders.

The info line shows which paragraph you are on, plus the percentage and time
listened while narration is running.

### Gestures on the text

- **Double tap** → narration starts **exactly from the sentence** you tapped.
- **Long press** → bookmark for that paragraph (a 🔖 appears in front of it).

The sentence being read is **highlighted**, its paragraph faintly tinted — so
you can follow along visually.

### In-text search

Opens with the 🔍 icon. Searches from 2 characters up, **ignoring accents**
(typing "varazslono" finds "varázslónő"). Hits are highlighted, a counter
shows your position (e.g. `3/17`), and the ▲▼ buttons jump between them.

### Bookmarks

Long-press anywhere in the text, or use the 🔖+ button. While narration is
running the bookmark lands **on the sentence being read**, not on whatever
you happen to be looking at.

The list behind the 🔖 button shows the paragraph number, the date and a
snippet; tap to jump there, use the bin icon to delete.

### Chapters

Chapter navigation works per format:

- **epub** — real chapter documents and headings,
- **mobi, html** — headings (`h1`–`h6`),
- **fb2** — section titles,
- **txt, rtf, pdf, docx** — heading heuristics (numbered chapters, roman
  numerals, all-caps lines).

If no chapter markers are found at all, the button falls back to ~5% jumps so
it is never useless.

## 8. Headset buttons

Works with both Bluetooth and wired headsets, including single-button types:

| Press | Effect |
|---|---|
| **1×** | play / pause |
| **2×** | rewind ~5 seconds |
| **3×** | also rewinds |

The "5 seconds" is an estimate: TTS has no real timeline, so the app converts
it from the configured speech rate and snaps to a sentence boundary.

Button control stays alive while narration is playing or paused (the
notification is visible). Once you fully stop it with ⏹, the headset button
no longer restarts it — deliberately, so it cannot start talking in your
pocket.

## 9. Reading list and export

Opens with the 📊 icon in the browser.

Four figures at the top: **started · finished · in progress · total listening
time**. Below, two sections:

- **📖 "Folyamatban"** (in progress) — started but not finished,
- **✔ "Elolvasott"** (finished) — above 98%.

Each book shows a progress bar, exact percentage, listening time, last date,
and three buttons: **▶ read aloud**, **📖 read on screen**, **🗑 remove from
the list**.

Progress counts the **higher** of on-screen reading and narration.

### Export

Two buttons in the header:

- **💾 Save** — writes the record into `Download/KonyvtarTTS/`:
  - `olvasas_<date>.csv` — status, title, author, completion %, paragraph,
    minutes listened, last opened, file path, book ID
  - `konyvjelzok_<date>.csv` — every bookmark with its snippet
  - `konyvtar_tts_<date>.db` — a copy of the app's whole database
- **📤 Share** — runs the same export, then hands the CSVs to e-mail, cloud
  storage, anything.

The CSV is **semicolon-separated with a UTF-8 BOM**, so Excel and LibreOffice
open it correctly on a double click. The `.db` copy is plain SQLite, so you
can query it on a PC with any tool (e.g. Python's `sqlite3`).

## 10. Settings

- **Catalogue database** — current path and state, and a picker to change it.
- **Books root folder** — where the browser opens on start.
- **Build catalogue from books** — see [10/a](#10a-building-a-catalogue-from-your-books).
- **Caches** — how many files you have scanned and how much extracted text is
  stored; both can be cleared. (Clearing does **not** touch your positions.)
- **Text-to-speech** — a shortcut to the system TTS settings, where you can
  change engine and voice.

## 10/a. Building a catalogue from your books

With no prepared catalogue, the app **can build one itself** — from the
metadata stored inside the book files, with no internet.

**How:** Settings → *"Katalógus építése a könyvekből"* (build catalogue from
books) → **Build**. It walks the current root folder recursively and writes
the result to `Download/KonyvtarTTS/sajat_katalogus.db`. When it finishes, one
button puts it straight into use.

**Re-running after adding books:** the button then reads *"Frissítés az új
könyvekkel"* (update with new books). **Existing entries are left alone** — it
recognises by file path what is already in there and only processes new files.
So the second run is much faster, and anything you fixed by hand or on the PC
survives.

### What it finds per format

| Format | Extracted |
|---|---|
| **EPUB** | title, author, publisher, year, ISBN, language, **synopsis**, series + index, tags |
| **FB2** | author, title, **annotation**, genre, series, publisher, year, ISBN |
| **MOBI/AZW3** | title, author, publisher, **description**, ISBN, subjects, date |
| **DOCX** | title, author, description, keywords |
| **RTF** | title, author, subject, comment (when filled in) |
| **PDF** | title, author, keywords — **toggleable**, because it is slower |
| **TXT and the rest** | title and author guessed from the file name |

The **PDF toggle** exists because reading PDF metadata requires walking the
file structure, which is slow for large files. On top of that, PDF "titles"
are often junk (the scanner software's name, a file name, "Microsoft Word -
something.doc"), so the app filters obviously useless values out and falls
back to the file name.

### Worth knowing

- **It cannot invent a synopsis.** Where the file carries no description
  (typically scanned PDFs and TXT files), the synopsis stays empty.
- **Duplicates are merged:** if the same book exists as both epub and mobi,
  one catalogue entry is created with two files, based on an
  accent-insensitive comparison of title and author.
- **The schema is identical** to the PC-built catalogue (`konyvek` +
  `fizikai_fajlok`), so the file opens in any SQLite tool on your computer
  and the app treats it the same way.
- **Bad metadata inside a file:** some books have title and author swapped,
  or fields missing. The app writes whatever the file says — fix it in the
  source file with Calibre and rebuild.
- **Old entries persist.** To remove things, delete the whole
  `sajat_katalogus.db` and build again.

## 11. Troubleshooting

**Red "nincs DB" in the header**
The catalogue was not found. Settings → select database file. Check that you
copied the `.db` itself (not the `-wal`/`-shm` files).

**I cannot see the SD card**
Use the 💾 icon at the start of the path row to switch volumes. If the card is
not listed, the system has not mounted it.

**"Ez a könyv DRM-védett" (this book is DRM protected)**
Mobipocket-encrypted files cannot be opened. Convert them (e.g. with Calibre)
to a free format.

**"A PDF nem tartalmaz szövegréteget" (the PDF has no text layer)**
It is a scanned, image-based PDF. Run OCR on it, then read the resulting
`.txt`.

**`.doc` files will not play**
The old binary `.doc` format is not supported — convert to `.docx` or `.txt`.

**Narration stops after a while**
Battery optimisation likely killed the service. Exempt the app in system
settings (Settings → Apps → Könyvtár TTS → Battery → Unrestricted).

**No voice for my language**
Settings → Text-to-speech → in the system TTS settings, download the language
pack for Google Text-to-Speech.

**The first open is slow**
The first time, the app extracts the entire text from the book (which can take
a while for a large PDF). Afterwards it comes from the cache and is instant.
