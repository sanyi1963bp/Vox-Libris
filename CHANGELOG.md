# Változásnapló / Changelog

🇭🇺 [Magyar](#magyar) · 🇬🇧 [English](#english)

---

## Magyar

A formátum a [Keep a Changelog](https://keepachangelog.com/hu/1.0.0/) ajánlást
követi, a verziószámozás a [SemVer](https://semver.org/lang/hu/) szerint megy.

### [1.4.1] — 2026-08-29

- **"Almappák is" kapcsoló** a kereső mellett: bekapcsolva az aktuális mappa
  teljes mappafájában keres, nem csak egy szinten. A találatokat a
  fájlrendszerből (fájlnév szerint) és a szkennelési gyorsítótárból (cím és
  szerző szerint is) fésüli össze.

### [1.4.0] — 2026-08-29

**Egyszerűbb fájllista**

- Megszűnt a külön "Katalógus" nézet és a nézetváltó gombok: **csak a
  fájllistát látod**. A katalógus a program belső ügye — abból tölti ki a
  szerzőt, címet és a leírást.
- Minden könyv mellett egy **ⓘ gomb**: megnyitja a könyv adatait (szerző, cím,
  kiadó, év, sorozat, címkék, fülszöveg). Ha nincs katalógus-találat, a
  **fájl saját metaadatát** olvassa ki helyben.
- A már elkezdett könyvek alatt **olvasottsági csík** látszik a
  százalékkal, a befejezetteknél "kész" felirattal.

**Változás**

- A bekezdések előtti jelzőhang megszűnt. Fejezet előtt továbbra is szól a
  mélyebb, kettős hang.

### [1.3.0] — 2026-08-29

**Megjelenés**

- **Téma**: rendszer szerint / világos / sötét, kézzel választható.
- **Hat színséma**: Klasszikus zöld, Tenger kék, Szépia (papír), Naplemente,
  Éjszakai (kímélő, fekete háttérrel) és Magas kontraszt.
- **A kezelőfelület betűmérete** külön állítható (80–160%), a könyv szövegének
  mérete pedig továbbra is az olvasóban.
- **Vérvörös sáv** jelzi a fejezethatárokat a szövegben — messziről látszik,
  hol kezdődik új fejezet.

**Felolvasás nyelve**

- Új beállítás: a felolvasás nyelve a telepített TTS motor **összes elérhető
  nyelvéből** kiválasztható (nem csak magyar). Alapértelmezés továbbra is
  automatikus: magyar, ha van, egyébként a rendszer nyelve.
- **Hangok letöltése** gomb: közvetlenül megnyitja a TTS motor hangletöltőjét.

**Egyéb**

- A szkennelés a beállításokból is indítható, haladásjelzéssel. Automatikusan
  továbbra sem indul soha.
- A könyv végén a felolvasás megáll és vár — nem lép tovább magától.

### [1.2.0] — 2026-08-29

**Egyetlen könyv-képernyő**

- Megszűnt a külön részletező ablak: a könyvnek **egy képernyője** van, ahol a
  szöveg és minden vezérlő együtt van. A böngészőben egy koppintás megnyitja
  az olvasót, dupla koppintás egyből felolvasással indít.
- A könyv adatai (metaadat + fülszöveg) az olvasó „További műveletek"
  menüjéből, ablakban nyílnak.
- A **beállítás gomb felülre**, minden **léptetőgomb alulra** került.

**Teljes léptetősor**

- Alul, egy sorban: **fejezet ◀ · bekezdés ◀ · mondat ◀ · lejátszás/szünet ·
  mondat ▶ · bekezdés ▶ · fejezet ▶**, mindegyik felirattal.
- A bekezdés-vissza gomb előbb az aktuális bekezdés elejére ugrik, csak utána
  az előzőre (mint a zenelejátszókban).
- A gombok akkor is működnek, ha még nem ez a könyv szól: ilyenkor a
  látott helyről indítják a felolvasást.

**Hangjelzések**

- Halk, rövid jelzőhang minden **bekezdés** előtt.
- Mélyebb, kettős, ereszkedő hang minden **fejezet** előtt (kb. fél másodperc).
- Mindkettő külön kapcsolható, közös hangerő-szabályzóval.

**Egyéb**

- Az éppen felolvasott mondat háttere jól láthatóan kiemelve, a bekezdése
  halványan színezve; a szöveg alapból **követi a felolvasást**.
- Fejezetkezdet előtt elválasztó vonal, félkövér címsor.
- Új beállítások: hangjelzések, követés, képernyő ébren tartása, a fülhallgató
  dupla nyomására visszaugrott másodpercek (3–30).

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

### [1.4.1] — 2026-08-29

- **"Almappák is" (include subfolders) toggle** next to the search box: when
  on, the search covers the whole tree under the current folder instead of a
  single level. Results merge the file system (by file name) with the scan
  cache (which also matches title and author).

### [1.4.0] — 2026-08-29

**A simpler file list**

- The separate "catalogue" view and its switcher buttons are gone: **you only
  see the file list**. The catalogue is now purely internal — it fills in
  author, title and description.
- Every book has an **ⓘ button** opening its details (author, title,
  publisher, year, series, tags, synopsis). With no catalogue match it reads
  the **file own embedded metadata** on the spot.
- Books you have started show a **progress bar** with the percentage, and
  "kész" (done) once finished.

**Changed**

- The cue before each paragraph is gone. The deeper double tone before each
  chapter stays.

### [1.3.0] — 2026-08-29

**Appearance**

- **Theme**: follow system / light / dark, chosen by hand.
- **Six colour schemes**: Classic green, Ocean blue, Sepia (paper), Sunset,
  Night (black background, easy on the eyes) and High contrast.
- **Interface font size** is now adjustable on its own (80–160%); the book
  text size stays in the reader.
- A **blood-red band** marks chapter boundaries in the text, visible at a
  glance.

**Narration language**

- New setting: the narration language can be picked from **every language the
  installed TTS engine offers**, not just Hungarian. The default stays
  automatic: Hungarian if available, otherwise the system language.
- **Download voices** button: opens the TTS engine's voice installer directly.

**Other**

- Scanning can also be started from settings, with progress. It still never
  starts on its own.
- At the end of a book narration stops and waits — it never moves on by itself.

### [1.2.0] — 2026-08-29

**A single book screen**

- The separate details window is gone: a book has **one screen** holding both
  the text and every control. A single tap in the browser opens the reader; a
  double tap starts narration right away.
- Book metadata and synopsis now open in a dialog from the reader's overflow
  menu.
- The **settings button moved to the top**, every **transport button to the
  bottom**.

**Full transport row**

- One row at the bottom: **chapter ◀ · paragraph ◀ · sentence ◀ · play/pause ·
  sentence ▶ · paragraph ▶ · chapter ▶**, each with a caption.
- Paragraph-back first jumps to the start of the current paragraph, then to
  the previous one (as music players do).
- The buttons work even when this book is not the one playing: they start
  narration from the visible position.

**Audio cues**

- A soft, short cue before every **paragraph**.
- A deeper, descending double tone before every **chapter** (about half a
  second).
- Both toggle independently, with a shared volume slider.

**Other**

- The sentence being read is clearly highlighted, its paragraph faintly
  tinted; the text **follows narration** by default.
- Chapter starts get a divider and a bold heading.
- New settings: audio cues, follow mode, keep screen on, and the headset
  double-press rewind length (3–30 seconds).

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
