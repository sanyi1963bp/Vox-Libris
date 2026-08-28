# Változásnapló / Changelog

🇭🇺 [Magyar](#magyar) · 🇬🇧 [English](#english)

---

## Magyar

A formátum a [Keep a Changelog](https://keepachangelog.com/hu/1.0.0/) ajánlást
követi, a verziószámozás a [SemVer](https://semver.org/lang/hu/) szerint megy.

### [1.1.0] — 2026-08-28

**Hozzáadva — katalógusépítés a könyvfájlokból**

- Az app immár **saját katalógust tud készíteni** a telefonon lévő könyvek
  beágyazott metaadataiból, internet nélkül: EPUB (OPF), FB2 (`title-info`),
  MOBI/AZW3 (EXTH fejléc), DOCX (`core.xml`), RTF (`\info`), PDF
  (dokumentum-információ). Cím, szerző, fülszöveg, kiadó, év, ISBN, sorozat,
  címkék és nyelv.
- **Inkrementális frissítés:** újrafuttatáskor a már bejegyzett fájlokat
  (útvonal szerint) érintetlenül hagyja, csak az újakat dolgozza fel.
- **Duplikátumok összevonása** normalizált cím + szerző alapján: ugyanaz a
  könyv több formátumban egyetlen bejegyzést kap, több fájllal.
- Az eredmény sémája **azonos** a PC-n készült katalóguséval, helye
  `Download/KonyvtarTTS/sajat_katalogus.db`.
- A PDF metaadat-olvasás **kapcsolható** (lassabb), és az app kiszűri a
  tipikus PDF-szemetet („Microsoft Word - …", fájlnevek, szkennerprogramok).
- Ahol nincs beágyazott metaadat, a cím és a szerző a **fájlnévből** áll elő.

### [1.0.0] — 2026-08-28

Első nyilvános kiadás.

**Katalógus és böngésző**
- Külső SQLite katalógus (~68 000 könyv) megnyitása csak olvasásra, helyben.
- Fájl↔könyv párosítás: elsődlegesen a katalógusban tárolt fájlnév-index
  alapján, tartalékként a fájlnévből kinyert cím + szerző egyeztetésével
  (ékezet- és írásjel-független normalizálás).
- Total Commander-stílusú, ikonmentes böngésző; mappanézet és lapos
  katalógusnézet; rendezés oszlopfejlécre koppintva; keresés fájlnév, cím és
  szerző szerint; gyorsgörgető sáv.
- Rekurzív, megszakítható, **inkrementális** szkennelés (a változatlan
  fájlokat nem dolgozza fel újra).
- Tárolóváltó: belső tároló, SD-kártya, USB.

**Felolvasás**
- Rendszer TTS motor használata, előtér-szolgáltatásban (háttérben is szól,
  értesítési sávból vezérelhető).
- **Mondatszintű** feldolgozás: mondatonkénti léptetés, mondatpontos
  pozíciómentés és folytatás, az aktuális mondat kiemelése.
- Dupla koppintás a szövegen: felolvasás pontosan a megérintett mondattól.
- Sebesség- (0,5×–3×) és hangmagasság-szabályzás.
- Fülhallgató-gombok MediaSessionön át: 1 nyomás = start/stop,
  2 nyomás = ~5 másodperc vissza.
- Teljes szöveg átadása külső TTS alkalmazásnak (`ACTION_SEND`).

**Olvasó képernyő**
- Egyesített olvasó + lejátszó: a szöveg és minden vezérlő egy képernyőn.
- Navigáció: fejezet, képernyőnyi lapozás, mondat, pozíció-csúszka.
- Fejezetfelismerés: EPUB spine és címsorok, MOBI/HTML címsorok, FB2
  szekciócímek, heurisztika txt/rtf/pdf/docx esetén.
- Követés mód: a szöveg magától gördül a felolvasott résszel.
- Állítható betűméret, megjegyzett értékkel.
- Keresés a szövegben, ékezet-függetlenül, találatszámlálóval.
- Könyvjelzők: hozzáadás hosszú nyomással, lista, ugrás, törlés.

**Nyilvántartás**
- Olvasási lista két kategóriában: elolvasott (98% fölött) és folyamatban.
- Könyvenként: haladás, hallgatási idő, utolsó hozzáférés.
- Exportálás CSV-be (UTF-8 BOM, pontosvessző) és SQLite-másolatba a
  `Download/KonyvtarTTS/` mappába, illetve megosztás.

**Formátumok**
- Saját olvasó: EPUB, MOBI/PRC/AZW/AZW3 (PalmDOC kitömörítéssel), FB2, RTF
  (Windows-1250 kódlappal is), DOCX, TXT, HTML.
- PDF: szövegréteg kinyerése PDFBox-Androiddal.
- Érthető magyar hibaüzenet DRM-es, HUFF/CDIC tömörítésű és képalapú
  (szkennelt) fájloknál.

**Egyéb**
- Nincs borítókép-kezelés — ez tudatos döntés a sebesség és a memória
  érdekében.
- Nincs internet-engedély: az app technikailag képtelen adatot küldeni.

#### Fejlesztési mérföldkövek

| Dátum | Mi készült el |
|---|---|
| 2026-08-26 | Alapprojekt: katalógus, böngésző, TTS-szolgáltatás, olvasási pozíciók, első működő APK |
| 2026-08-26 | SD-kártya és tárolóváltó támogatás |
| 2026-08-26 | Képernyős olvasó, könyvjelzők, szövegkeresés, olvasási lista |
| 2026-08-28 | Mondatszintű felolvasás, fejezetnavigáció, képernyőnyi lapozás |
| 2026-08-28 | Fülhallgató-gombok (MediaSession) |
| 2026-08-28 | Olvasó és lejátszó képernyő egyesítése |
| 2026-08-28 | Olvasási nyilvántartás exportálása |

---

## English

This project adheres to [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
and [Semantic Versioning](https://semver.org/).

### [1.1.0] — 2026-08-28

**Added — building a catalogue from the book files**

- The app can now **build its own catalogue** from metadata embedded in the
  books on the phone, with no internet: EPUB (OPF), FB2 (`title-info`),
  MOBI/AZW3 (EXTH header), DOCX (`core.xml`), RTF (`\info`), PDF (document
  information). Title, author, synopsis, publisher, year, ISBN, series, tags
  and language.
- **Incremental updates:** on re-run, files already recorded (by path) are
  left untouched and only new ones are processed.
- **Duplicate merging** by normalised title + author: the same book in
  several formats gets a single entry with multiple files.
- The result uses the **same schema** as the PC-built catalogue, stored at
  `Download/KonyvtarTTS/sajat_katalogus.db`.
- PDF metadata reading is **toggleable** (it is slower), and the app filters
  out the usual PDF junk ("Microsoft Word - …", file names, scanner software).
- Where no embedded metadata exists, title and author are derived from the
  **file name**.

### [1.0.0] — 2026-08-28

First public release.

**Catalogue and browser**
- Opens an external SQLite catalogue (~68,000 books) read-only, in place.
- File↔book matching: primarily via the file name index stored in the
  catalogue, falling back to title + author parsed from the file name
  (accent- and punctuation-insensitive normalisation).
- Total Commander style icon-free browser; folder view and flat catalogue
  view; sort by tapping a column header; search by file name, title and
  author; fast-scroll bar.
- Recursive, cancellable, **incremental** scanning (unchanged files are not
  reprocessed).
- Storage switcher: internal storage, SD card, USB.

**Narration**
- Uses the system TTS engine in a foreground service (keeps playing in the
  background, controllable from the notification shade).
- **Sentence-level** processing: sentence stepping, sentence-accurate
  position saving and resume, highlighting of the current sentence.
- Double tap on the text: narration starts exactly from the tapped sentence.
- Speed (0.5×–3×) and pitch control.
- Headset buttons through MediaSession: 1 press = play/pause,
  2 presses = rewind ~5 seconds.
- Hand the full extracted text to an external TTS app (`ACTION_SEND`).

**Reader screen**
- Unified reader + player: the text and every control on one screen.
- Navigation: chapter, screen-by-screen paging, sentence, position slider.
- Chapter detection: EPUB spine and headings, MOBI/HTML headings, FB2
  section titles, heuristics for txt/rtf/pdf/docx.
- Follow mode: the text scrolls along with the narration.
- Adjustable font size, remembered between sessions.
- Accent-insensitive in-text search with a match counter.
- Bookmarks: add by long press, list, jump, delete.

**Records**
- Reading list in two sections: finished (above 98%) and in progress.
- Per book: progress, listening time, last access.
- Export to CSV (UTF-8 BOM, semicolon separated) and an SQLite copy into
  `Download/KonyvtarTTS/`, or via the system share sheet.

**Formats**
- Own parsers: EPUB, MOBI/PRC/AZW/AZW3 (with PalmDOC decompression), FB2,
  RTF (including the Windows-1250 code page), DOCX, TXT, HTML.
- PDF: text layer extraction via PDFBox-Android.
- Clear error messages for DRM-protected, HUFF/CDIC compressed and
  image-based (scanned) files.

**Other**
- No cover image handling — a deliberate decision for speed and memory.
- No internet permission: the app is technically incapable of sending data
  anywhere.

#### Development milestones

| Date | What landed |
|---|---|
| 2026-08-26 | Base project: catalogue, browser, TTS service, reading positions, first working APK |
| 2026-08-26 | SD card and storage switcher support |
| 2026-08-26 | On-screen reader, bookmarks, in-text search, reading list |
| 2026-08-28 | Sentence-level narration, chapter navigation, screen paging |
| 2026-08-28 | Headset buttons (MediaSession) |
| 2026-08-28 | Reader and player screens merged |
| 2026-08-28 | Reading record export |
