# Release szöveg — v1.0.0

Ez a fájl a GitHub Release létrehozásához készült: a lenti szöveget elég
bemásolni a Release leírásába. (Ez a dokumentum maga nem része az appnak.)

---

## Hogyan hozd létre a Release-t

1. A repóban: **Releases** → **Draft a new release**
   (közvetlen link: `https://github.com/sanyi1963bp/vox-libris/releases/new`)
2. **Choose a tag** → írd be: `v1.0.0` → *Create new tag: v1.0.0 on publish*
3. **Release title:** `Vox Libris 1.0.0`
4. **Description:** másold be az alábbi keretes szöveget (a `---` vonalak
   közöttit).
5. **Attach binaries:** húzd rá az APK-t:
   `app\build\outputs\apk\release\app-release.apk`
   Érdemes átnevezni feltöltés előtt: `vox-libris-1.0.0.apk`
6. **Publish release**

> Az APK szándékosan nincs a git repóban (a `build/` mappa ki van zárva) —
> egy 9 MB-os bináris fölöslegesen hizlalná a git történetét. A Release a
> helyes hely rá: onnan letölthető, de nem terheli a klónozást.

---

## A Release leírása (ezt másold be)

---

**Helyi e-könyvtár böngésző és felolvasó Androidra — nagyon nagy
könyvtárakhoz.** / *Local e-book library browser and TTS reader for Android,
built for very large libraries.*

### 🇭🇺 Magyar

Első nyilvános kiadás. Az app egy helyi SQLite katalógushoz (~68 000 könyv)
párosítja a telefonon lévő könyvfájlokat, és a rendszer szövegfelolvasójával
olvassa fel őket — mondatról mondatra, automatikus folytatással.

**Amit tud:**

- 📚 **Total Commander-stílusú böngésző** — sűrű, ikonmentes lista, rekurzív
  szkennelés, rendezés, keresés; borítóképeket szándékosan nem tölt be, ezért
  70 000+ rekordnál is villámgyors.
- 🔊 **Mondatszintű felolvasás** — dupla koppintás a szövegre, és pontosan
  attól a mondattól indul; az aktuális mondat kiemelve; a mentett pozíció is
  mondatpontos.
- 📖 **Egyesített olvasó** — szöveg és minden vezérlő egy képernyőn: fejezet,
  képernyőnyi lapozás, mondatléptetés, sebesség, hangmagasság, követés mód.
- 🔍 **Keresés a szövegben** ékezet-függetlenül, 🔖 **könyvjelzők**.
- 🎧 **Fülhallgató-gombok** (Bluetooth és vezetékes): 1 nyomás start/stop,
  2 nyomás 5 másodperc vissza.
- 📊 **Olvasási lista** elolvasott / folyamatban bontásban, **CSV és SQLite
  exporttal** a PC-re.
- 🔒 **Nincs internet-engedély** — az app technikailag képtelen adatot küldeni.

**Formátumok:** EPUB, MOBI/PRC/AZW3, FB2, PDF (szövegréteg), RTF, DOCX, TXT,
HTML.

**Telepítés:** töltsd le a `vox-libris-1.0.0.apk` fájlt, másold a telefonra és
telepítsd (az „ismeretlen forrás” engedélyezése kellhet). Első indításkor add
meg a „Minden fájl kezelése” engedélyt.
Részletes útmutató: [docs/HASZNALAT.md](../blob/main/docs/HASZNALAT.md)

**Igény:** Android 11 vagy újabb; rendszer TTS motor magyar hanggal (pl.
Google Szövegfelolvasó).

### 🇬🇧 English

First public release. The app matches the book files on your device against a
local SQLite catalogue (~68,000 records) and reads them aloud with the system
TTS engine — sentence by sentence, with automatic resume.

**Highlights:**

- 📚 **Total Commander style browser** — dense, icon-free rows, recursive
  scanning, sorting and search; cover images are never loaded, which keeps it
  instant with 70,000+ records.
- 🔊 **Sentence-level narration** — double tap the text and it starts from
  exactly that sentence; the current sentence is highlighted; the saved
  position is sentence-accurate.
- 📖 **Unified reader** — text and every control on one screen: chapter,
  screen paging, sentence stepping, speed, pitch, follow mode.
- 🔍 **Accent-insensitive in-text search**, 🔖 **bookmarks**.
- 🎧 **Headset buttons** (Bluetooth and wired): 1 press play/pause, 2 presses
  rewind 5 seconds.
- 📊 **Reading list** split into finished / in progress, with **CSV and SQLite
  export** for the PC.
- 🔒 **No internet permission** — the app is technically incapable of sending
  data anywhere.

**Formats:** EPUB, MOBI/PRC/AZW3, FB2, PDF (text layer), RTF, DOCX, TXT, HTML.

**Install:** download `vox-libris-1.0.0.apk`, copy it to the phone and install
(you may need to allow "unknown sources"). Grant the "All files access"
permission on first launch.
Full guide: [docs/USAGE.en.md](../blob/main/docs/USAGE.en.md)

**Requirements:** Android 11 or newer; a system TTS engine with a voice for
your language.

> **Note:** the user interface is in Hungarian.

---
