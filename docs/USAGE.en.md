# Vox Libris — usage guide

🇬🇧 English (this page) · 🇭🇺 [Magyar](HASZNALAT.md) · ⬅ [Back to the main page](../README.en.md)

> **Installing**, setting up the text-to-speech engine and its voices, the
> Bluetooth details and the build dependencies are on a separate page:
> **[Setup and dependencies](SETUP.en.md)**

---

## Contents

1. [What you need](#1-what-you-need)
2. [Installing](#2-installing)
3. [First start — permissions](#3-first-start--permissions)
4. [Reading in your library](#4-reading-in-your-library)
5. [Library — the start screen](#5-library--the-start-screen)
6. [File browser](#6-file-browser)
7. [The context menu](#7-the-context-menu)
8. [Shelf](#8-shelf)
9. [Reader screen](#9-reader-screen)
10. [Headset buttons](#10-headset-buttons)
11. [Reading list and export](#11-reading-list-and-export)
12. [Settings](#12-settings)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What you need

- An **Android 11 or newer** phone.
- A **system TTS engine** with a voice for your language. Most phones already
  have Google Text-to-Speech; if a voice is missing, one button in the app's
  settings downloads it.
- **Books on the phone**, in one folder — subfolders are fine, and an SD card
  works too.

No account, no internet, no prepared database.

## 2. Installing

The easiest way is the
**[download page](https://sanyi1963bp.github.io/Vox-Libris/docs/)**: open it on
the phone and one button installs the app.

By hand: download the APK from the
[Releases](https://github.com/sanyi1963bp/Vox-Libris/releases) page and open it
on the phone. The system will warn you about an app from an unknown source —
that is normal, because it does not come from the Play Store; you have to allow
installing from whatever app you opened it with.

## 3. First start — permissions

The app asks for **one permission that matters**: **"All files access"**.
Without it, it cannot see your books. The button takes you to the system
settings, where you switch it on.

From Android 13 it also asks for **notification permission**. Everything works
without it, you just get no controls on the lock screen.

**It does not ask for internet access, and cannot** — the permission is not in
the manifest. All data stays on the phone.

## 4. Reading in your library

On first start the app asks **where your books are**, then offers to read them
in. This happens in two passes:

**First pass — metadata.** It walks the folder and all its subfolders, reads
the title, author, description, publisher, series, ISBN and tags out of the
files, and builds a catalogue from them. A thousand books takes about a minute.
When it finishes, your library is already usable.

**Second pass — covers.** It starts in the background and extracts the cover
images from the files. This is slower (decoding an image, rendering a PDF
page), which is why it runs separately — you can use the app meanwhile.

### Running it again

After copying in new books: **Settings → Catalogue → Read the library**. The
scan is **incremental** — existing entries are left alone, only new books are
added. The same goes for covers: what was extracted once is never attempted
again.

### Where the catalogue lives

```
Download/KonyvtarTTS/sajat_katalogus.db
```

Deliberately a **visible file**, not in the app's private storage: it survives
reinstalling the app and can be opened on a PC with any SQLite tool. If you
delete it, you only need to run the scan again — your reading progress is not
kept in it.

### Worth knowing

- **It cannot invent a description.** Where the file contains none (typically
  scanned PDFs and TXT), it stays empty.
- **Duplicates are merged:** if you have the same book as both epub and mobi,
  one catalogue entry is created with two files, based on an
  accent-insensitive comparison of title and author.
- **Bad metadata in the file:** sometimes a book has the title and author
  swapped, or missing. The app writes down what is in the file — fix it in the
  source file with Calibre and scan again.

## 5. Library — the start screen

All your books in one list. **Swipe** left or right to switch to the
[file browser](#6-file-browser); two dots next to the title show where you are.

### Search

The search box looks at the **title, the author, the file name and your own
notes** at once, accent-insensitive. It updates as you type, even with
thousands of books.

### Letter bar

Under the search box. One tap on an initial keeps only those books; tapping the
same letter again clears the filter.

The bar **shows only letters you actually have books for** — no buttons leading
nowhere. It follows the sort order (title or author), and accented letters fold
into their base letter.

> The letter bar is itself horizontally scrollable, so a swipe there moves the
> bar first. To switch views, swipe over the list.

### Sorting and the format filter

Two icons next to the search box:

| Icon | What it does |
|---|---|
| **sort** | by title, author or format; choosing it again reverses the order |
| **filter** | formats with counts (EPUB 2100, PDF 900…), one tap narrows the list |

The format filter doubles as an **overview**: it tells you what is on your
phone.

### What a row shows

On the left the **format badge**, then the title, and under it the author and
the file name. If you started the book, a **green progress bar** at the bottom.
If it has a note, a small ✎ next to the title.

**A grey format badge means no text can be extracted from that file** — that
book will not speak.

### Taps

| Gesture | What it does |
|---|---|
| **single tap** | selects the row |
| **double tap** | opens it **and reads it aloud** from the last position |
| **long press** | [context menu](#7-the-context-menu) |

### Top bar

| Icon | Function |
|---|---|
| **shelf** | swipeable cover view |
| **folder** | swipes to the file browser |
| **bar chart** | reading list and statistics |
| **gear** | settings |

Below it, in one line: how many books the filter shows out of the total, and on
the right the **Finished / In progress** counters — tapping one opens the list.

## 6. File browser

The same books, by folder. A dense, icon-free list so that as many rows as
possible fit on one screen.

### Top bar

| Element | What it does |
|---|---|
| **book icon** | swipes back to the library |
| **radar** | read the library |
| **bar chart** | reading list and statistics |
| **gear** | settings |
| **search box** | filter by file name, title and author |
| **"Subfolders too"** | the search covers the whole tree, not just one level |
| **SD card icon** | switch storage (internal, SD card, USB) |
| **⬆ arrow** | one level up |

### Columns and sorting

The header has **Name · Author · Size · Date** — tap any of them to sort by it;
tap again to reverse.

Every row has two parts: the file name, size and date on top; underneath — if
it is in the catalogue — the author and the title.

### Taps

| Gesture | What it does |
|---|---|
| **single tap** | open the folder, or open the book for reading |
| **double tap** | open **and read aloud** |
| **long press** | [context menu](#7-the-context-menu) |

The **ⓘ** button at the end of the row opens the details sheet directly.

### Moving quickly

There is a **fast scrollbar** along the right edge — drag it and you fly
through tens of thousands of rows.

## 7. The context menu

**It opens on long press, and it is the same in both views.**

| Entry | What it does |
|---|---|
| **Details** | cover, description, all metadata, how far you are |
| **Note** | your own note on the book (saving it empty deletes it) |
| **Rename** | within the file's own folder |
| **Move** | to another folder, with a small folder browser |
| **Copy** | to another folder; the original stays |
| **Delete** | permanent — always asks, and shows the file name |

### Why in the app and not in a file manager

This menu does not just move the file: **everything attached to it follows** —
the catalogue entry, the reading progress, the bookmarks, the note and the
thumbnail.

Do the same in a file manager and all of that stays behind on the old path, and
the book turns up **as new, from zero**.

### About deleting

There is no undo and no trash. The confirmation dialog shows the file name —
read it before you agree.

### In the reader

For the book you are currently reading there are **no file operations**: it
will not be renamed or deleted from under you, because that would break the
narration path and the position saving. **You can still write a note** — in
fact that is where it is most useful.

## 8. Shelf

A swipeable cover view: browse your books as if standing in front of a shelf.
It opens from the **shelf icon** in the top bar.

It shows whatever the **list currently filters** — if you searched for
something or narrowed it to one letter, only those books are on the shelf.

Under the cover: title, author and a progress bar. Tap to open the book, long
press for its details.

Where no cover could be extracted from the file, a **drawn cover** appears,
made from the title and the author — the colour comes from the title, so the
same book always looks the same.

## 9. Reader screen

This is the heart of the app: **you read here and you listen here** — there is
no separate player.

You can get here by double-tapping in the list or the browser, from the details
sheet, from the reading list, by tapping the now-playing bar, or from the
notification.

### Top bar

| Icon | Function |
|---|---|
| **⬅** | back |
| **🔍** | search inside the text (accent-insensitive) |
| **gear** | settings |
| **⋮** | bookmark here · bookmark list · details · stop narration |

### Bottom controls

At the top a status line: which paragraph you are on, and while narrating, the
chapter, the percentage and the time spent listening. Next to it two toggles:

- 🎯 **follow** — the text scrolls along with the narration (if you scroll
  yourself, it does not jump out from under your hand),
- 🎚 **tuning** — opens font size, speed (0.5×–3×) and pitch.

Under that the **position slider** with a percentage, and at the bottom the
transport:

| ⏫ | ⏮ | ◀ | ▶/⏸ | ▶ | ⏭ | ⏬ |
|---|---|---|---|---|---|---|
| chapter back | paragraph back | sentence back | play/pause | sentence forward | paragraph forward | chapter forward |

### Gestures on the text

- **Double tap** → narration starts **exactly from the sentence** you touched.
- **Long press** → an **action menu** for the sentence you pressed on.

The sentence being spoken is **highlighted**, and its paragraph faintly tinted
— so you can follow along by eye.

When you jump here from the bottom bar, **the current sentence glows brighter
for a few seconds** before settling back. You never have to hunt for where you
are.

### The bottom navigation bar

**Every view** carries a bar at the bottom with three buttons:

| Button | Where it goes |
|---|---|
| **Library** | the list start screen |
| **Files** | the folder browser |
| **Reading** | the text of the book you are listening to |

The active view is highlighted, so you can see where you are, not just where
you can go.

The **Reading** button works even when nothing is playing: it opens the book
you listened to last. It stays greyed out only until you have opened a book
for the first time.

Swiping between the library and the file browser **still works** — the bar's
buttons drive the same pager, so the two do not fight.

The **folder picker has no bar**, deliberately: that is a task to finish, not
a view.

### The text's action menu

The menu shows **which sentence** it applies to at the top — the same chunk
the narrator speaks as one unit. It has five entries:

| Entry | What it does |
|---|---|
| **Bookmark** | 🔖 on that paragraph |
| **Pronunciation** | teach the voice how to say a word |
| **Wikipedia** | opens the chosen word in the browser |
| **Quote card** | draws an image from the sentence and shares it |
| **Copy** | puts the sentence on the clipboard |

**Pronunciation** and **Wikipedia** act on a single word, so the menu offers
the sentence's words as tappable chips. You never have to select text — that
is deliberate: selection would fight the double tap that starts narration.

**If you would rather have the instant bookmark**, switch it under *Settings →
Reading and controls*. Long press then bookmarks right away, and the action
menu opens on a **single tap**.

### The pronunciation dictionary

Synthetic voices routinely mangle invented and foreign names, and changing the
speech rate does not help. When you hear a name come out wrong:

1. long-press that sentence,
2. **Pronunciation**,
3. tap the word,
4. type how it should sound (e.g. `Bree` → `Bri`),
5. **Save**.

From then on **every book** says it that way. If narration was running, the
sentence is re-spoken straight away, so you can hear whether it worked.

Worth knowing:

- The rule is **anchored to the start of a word but leaves the ending alone**.
  A `Bree` rule therefore also catches "Breeben", "Breeből" and "Breevel" —
  in Hungarian the suffix sits at the end and the stem at the front.
- The price is that a short pattern can reach into a longer word. If you hit
  that, write a longer pattern.
- The substitution only touches **the text handed to the engine**. The book's
  own text is untouched: the highlight does not shift, and search keeps
  searching the original.
- The rules can be reviewed, deleted and added under *Settings →
  Pronunciation dictionary*.

### Bionic Reading

The first ~40% of every word is set in bold, so the eye can catch on word
beginnings. The switch is in the bottom bar's **tuning** panel next to the
font size (**B** icon), or under *Settings → Reading and controls*.

It has an extra use here: if you follow the narration with your eyes, it is
easier to stay with the voice.

### Search inside the text

Opens with 🔍. It searches from 2 characters, **accent-insensitively**. Hits are
highlighted, the counter shows where you are (e.g. `3/17`), and ▲▼ jump between
them.

### Bookmarks

From the text's action menu (*Bookmark*), or the ⋮ menu's *Bookmark here*.
With the latter, while narration is running the bookmark lands **on the spoken
position**, not where you happen to be looking.

The list shows the paragraph number, the date and a snippet; tap to jump there,
the bin icon deletes it.

### Chapters

Chapter stepping works differently per format:

- **epub** — from the real chapter files and the headings,
- **mobi, html** — from the headings (`h1`–`h6`),
- **fb2** — from the section titles,
- **txt, rtf, pdf, docx** — by heuristics (chapter words, Roman numerals,
  all-caps lines).

If no chapter marks can be found at all, the button falls back to ~5% jumps, so
it is never useless.

Chapter breaks are marked in the text by a **blood-red band**, and a deeper
double tone sounds before them during narration (switchable).

### Now-playing bar

While narration is running, the book's title and progress sit at the bottom of
**every other screen**. The button silences it from anywhere, and tapping the
bar takes you back to the book, right where it is.

## 10. Headset buttons

Works with Bluetooth and wired headsets, including single-button types:

| Presses | Effect |
|---|---|
| **1×** | play / pause |
| **2×** | ~5 seconds back |
| **3×** | also jumps back |

The "5 seconds" is an estimate: TTS has no real timeline, so the app converts
from the speech rate you set and snaps to a sentence boundary.

Button control lives as long as the reader is running or paused (the
notification is visible). If you stop it completely with **Stop**, the headset
button will not restart it — deliberately, so nothing starts talking in your
pocket.

## 11. Reading list and export

Opens from the 📊 icon in the top bar, or by tapping the counters at the top of
the library.

Four numbers on top: **Started · Finished · In progress · total listening
time**. Below, two sections:

- **📖 In progress** — the ones you started but have not finished,
- **✔ Finished** — the ones above 98%.

Each book has a progress bar, an exact percentage, listening time, the last
date, and three buttons: **▶ read aloud**, **📖 read**, **🗑 remove from the
list**.

Progress counts the **larger** of on-screen reading and narration.

### Export

Two buttons in the header:

- **💾 Save** — writes the record into `Download/KonyvtarTTS/`:
  - `olvasas_<date>.csv` — status, title, author, completion %, paragraph,
    minutes listened, last time, file path, book ID
  - `konyvjelzok_<date>.csv` — every bookmark with its snippet
  - `konyvtar_tts_<date>.db` — a copy of the app's whole database
- **📤 Share** — runs the same, then hands the CSVs to e-mail, cloud, anything.

The CSV is **semicolon-separated, UTF-8 with BOM**, so Excel and LibreOffice
open it correctly on a double click. The `.db` copy is SQLite, so it can be
queried on a PC with any tool (e.g. Python's `sqlite3`).

## 12. Settings

Cards, one under the other:

| Card | What it sets |
|---|---|
| **Books root folder** | where the scan looks for your books |
| **Catalogue** | how many works and files it holds, where the file is, whether to scan PDFs, **Read the library**, remove missing files |
| **Covers** | how many covers there are and how much space they take, **Load covers**, delete, and the **Covers in the list** toggle (off by default) |
| **Appearance** | light/dark/system theme, six colour schemes, interface font size |
| **Interface language** | ten languages, or follow the system |
| **Narration language** | any language of the installed TTS engine, plus a button to the voice downloader |
| **Audio cues** | chapter cue on/off, its volume |
| **Reading and controls** | follow, keep the screen on, Bionic Reading, what the long press does, how far the headset's double press jumps back |
| **Pronunciation dictionary** | the substitutions you have added, delete, add a new rule |
| **Cache** | how much extracted text is stored, clearable (it does not touch your positions) |
| **Text-to-speech engine** | jump to the system TTS settings |

> **The interface language is separate from the narration language.** You can
> read an English book with a Hungarian interface, or the other way round.

## 13. Troubleshooting

**The list is empty although I have books**
The scan has not run yet. Settings → Catalogue → Read the library. Also check
that the root folder points where you think it does.

**I see no covers, only coloured placeholders**
Covers load in the second pass. Settings → Covers → Load covers. Where the file
contains no cover (TXT, RTF, DOCX), the drawn one stays.

**I cannot see the SD card**
In the browser, switch storage with the 💾 icon. If the card does not appear,
the system has not mounted it.

**"This book is DRM protected"**
Mobipocket-encrypted files cannot be opened. Convert them (e.g. with Calibre)
to a free format.

**"The PDF has no text layer"**
A scanned, image-based PDF. It needs OCR first, after which it works as `.txt`.

**`.doc` files do not speak**
The old binary `.doc` is not supported — convert to `.docx` or `.txt`. That is
why their badge is grey in the list.

**Narration stops after a while**
Battery optimisation may have killed the service. In the system settings,
exempt the app from power restrictions (Settings → Apps → Vox Libris →
Battery → Unrestricted).

**There is no voice for my language**
Settings → Narration language → *Download voices*, or download the language
pack in the system TTS settings.

**The first open is slow**
The first time, the app extracts the whole text from the book (with a large PDF
this can take a while). After that it comes from the cache and is instant.

**Swiping does not change the view**
You are probably swiping on the letter bar, which scrolls itself. Try over the
list.

**I renamed a file and lost my progress**
If you renamed it outside the app, in a file manager, then yes — the app looks
on the old path. Use the [context menu](#7-the-context-menu); it carries
everything over.

**The voice mispronounces a name**
Long-press that sentence → *Pronunciation* → tap the word → type how it should
sound. From then on every book says it that way.

**Long press opens a menu, but I want the bookmark**
Settings → Reading and controls → *Long press adds a bookmark right away*. The
menu then opens on a single tap.

**Wikipedia does not open**
There is no browser on the device, or it cannot open a web address. The app
itself never goes online — it only asks the system to open the address.
