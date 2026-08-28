# Vox Libris — helyi e-könyvtár és felolvasó (Android)

> **In English:** A native Android e-book library browser and text-to-speech
> reader, built for very large local collections (70 000+ metadata records).
> It matches book files on the device against a local SQLite catalogue, then
> reads them aloud with the system TTS engine — sentence by sentence, with
> resume, bookmarks, in-text search and headset button control. No cover
> images are ever loaded, which keeps browsing fast on huge libraries.
> Supported formats: EPUB, MOBI/PRC/AZW3, FB2, PDF (text layer), RTF, DOCX,
> TXT, HTML. UI language is Hungarian.

Natív Android alkalmazás (Kotlin + Jetpack Compose), amely a PC-n készített
**ncore_konyvtar.db** katalógushoz (~68 000 könyv) párosítja a telefonon lévő
könyvfájlokat, és a rendszer szövegfelolvasójával (TTS) hangoskönyvként
olvassa fel őket. Borítóképekkel szándékosan nem foglalkozik — így marad
gyors 70 000+ rekord mellett is.

Az alkalmazás a telefonon **Könyvtár TTS** néven jelenik meg.

## Fő funkciók

- **Katalógus-integráció**: a `konyvek` tábla metaadatai (cím, szerző, leírás,
  kiadó, címkék…) + a `fizikai_fajlok` táblában a PC-n már elvégzett
  fájl↔könyv párosítás újrafelhasználása.
- **Total Commander-stílusú böngésző**: sűrű, ikonmentes lista (név, méret,
  dátum, párosított szerző/cím), mappánkénti navigáció, rekurzív szkennelés,
  oszlopfejlécre koppintva rendezés, gyorsgörgető sáv.
- **Koppintáslogika**: szimpla koppintás → részletező (metaadat + leírás +
  szöveg-előnézet); **dupla koppintás → azonnali felolvasás** a legutóbbi
  pozíciótól; **hosszú nyomás → olvasás képernyőn**.
- **TTS**: rendszer TTS motor (magyar hanggal), lejátszás/szünet,
  sebesség- és hangmagasság-csúszka, bekezdésléptetés, háttérben is szól
  (előtér-szolgáltatás, értesítési sávból vezérelhető). Külső TTS appnak
  a teljes szöveg átadható (Megosztás gomb, `ACTION_SEND`).
- **Haladás + olvasási lista**: pontos pozíció (mondat) automatikus mentése,
  automatikus folytatás, százalék, hallgatási idő, utolsó hozzáférés —
  könyvenként; a lista külön kategóriában mutatja az elolvasott és a
  folyamatban lévő könyveket. **Exportálás**: a lista fejlécében a mentés
  gomb CSV-be és SQLite-másolatba írja a nyilvántartást a
  `Download/KonyvtarTTS/` mappába (PC-re másoláshoz), a megosztás gomb
  pedig e-mailben/felhőbe küldi a CSV-ket.
- **Egyesített olvasó + lejátszó képernyő**: a teljes szöveg olvasható a
  képernyőn, és ugyanitt van minden felolvasás-vezérlő is (nincs külön
  lejátszó képernyő). Alsó vezérlősáv: fejezet ⏫⏬, képernyőnyi lapozás ⬆⬇,
  mondatléptetés ⏮⏭, Play/Pause; mellette betűméret, pozíció-csúszka,
  **követés** (a szöveg magától gördül a felolvasott résszel) és
  **hangolás** (sebesség/hangmagasság csúszkák). Fent: keresés, könyvjelző,
  könyvjelzőlista, Stop.
- **Mondatszintű felolvasás**: a TTS mondatonként halad; dupla koppintás
  bárhol a szövegen a **kijelölt mondattól** indítja a felolvasást; az
  éppen felolvasott mondat kiemelve látszik; a mentett pozíció is
  mondatpontos. Az olvasás és a felolvasás pozíciója külön van mentve.
- **Keresés a szövegben** (ékezet-független, találatról találatra ugrás) és
  **könyvjelzők** (hosszú nyomás egy bekezdésen — lista, ugrás, törlés).
- **Fülhallgató-gombok** (Bluetooth és vezetékes, MediaSession):
  1 nyomás = Start/Stop, 2 nyomás = ~5 másodperc vissza.

## Formátumtámogatás (felolvasáshoz)

| Formátum | Állapot |
|---|---|
| epub, txt, fb2, htm/html | teljes |
| mobi, prc, azw, azw3 | teljes (PalmDOC tömörítés; DRM-es és HUFF/CDIC fájlnál érthető hibaüzenet) |
| rtf | teljes (1250-es kódlappal is) |
| pdf | szövegréteg kinyerése (szkennelt PDF-nél hibaüzenet — OCR után .txt-ként megy) |
| docx | teljes |
| doc, djvu | csak listázás/párosítás — felolvasáshoz konvertálás kell (pl. Calibre) |

## Építés

Előfeltétel: Android Studio (vagy csak Android SDK + JDK 17).

```
cd KonyvtarTTS
gradlew.bat assembleRelease
```

Kimenet: `app/build/outputs/apk/release/app-release.apk` (debug kulccsal
aláírva, azonnal telepíthető). Android Studio-ból: nyisd meg a mappát,
Run ▶.

Minimum Android 11 (API 30), cél: Android 15.

## Telepítés és első indítás

1. Másold a telefonra az `app-release.apk`-t és telepítsd
   (ismeretlen források engedélyezése kellhet).
2. Másold a telefonra a **`ncore_konyvtar.db`** fájlt — csak magát a `.db`-t,
   a `-wal` és `-shm` fájlokat NE. Javasolt hely: a belső tároló gyökere
   vagy a `Download` mappa (ezeken a helyeken az app magától megtalálja;
   máshol a Beállítások → „Adatbázisfájl kiválasztása…” alatt tallózható).
3. Másold fel a könyvfájlokat egy tetszőleges mappába.
4. Első indításkor add meg a „Minden fájl kezelése” engedélyt (a gomb a
   rendszerbeállításokba visz), Android 13+ esetén az értesítési engedélyt is.
5. Navigálj a könyves mappádba, és nyomd meg a **radar ikont** (rekurzív
   szkennelés + párosítás). Ezután a „Katalógus” nézetben az összes
   szkennelt fájl egyben, kereshetően látszik.

**SD-kártya**: az útvonal-sor elején lévő SD-kártya ikonnal válthatsz a
belső tároló és a memóriakártya (vagy USB-tároló) között — a böngészőben
és a fájl-/mappaválasztóban is. Az adatbázist az app az SD-kártya gyökerében
és Download mappájában is keresi.

## Architektúra

```
app/src/main/java/hu/konyvtar/tts/
├── MainActivity.kt        – navigáció, engedély-képernyő
├── App.kt                 – app-szintű inicializálás
├── model/Models.kt        – adatosztályok
├── data/
│   ├── CatalogDb.kt       – a külső ncore_konyvtar.db (csak olvasás) + CatalogHolder
│   ├── AppDb.kt           – saját kis DB: szkennelési cache + olvasási pozíciók
│   ├── Matcher.kt         – fájlnév → könyv párosítás (normalizálás, cím+szerző)
│   ├── FileScanner.kt     – rekurzív szkennelés, inkrementális cache
│   └── Prefs.kt           – beállítások
├── reader/                – szövegkinyerés: Epub/Fb2/Mobi/Rtf/Pdf/Docx/Txt/Html
│   └── TextExtractor.kt   – egységes belépési pont + szöveg-gyorsítótár
├── tts/TtsService.kt      – előtér-szolgáltatás: TTS lejátszás, pozíciómentés, MediaSession
└── ui/                    – Compose képernyők (Explorer, Detail, Reader, Stats, Settings, FilePicker)
```

Tervezési döntések:

- **Az olvasási pozíciók az app saját adatbázisában** vannak, nem a
  katalógusban — így a katalógus PC-ről történő frissítése (újramásolása)
  sosem törli a haladást.
- A kinyert szöveg bekezdésenként **gyorsítótárba** kerül
  (`cacheDir/text/`), ezért az első megnyitás után a folytatás azonnali.
- A párosítás elsődlegesen a `fizikai_fajlok.fajl_nev` alapján történik
  (a PC-n már elvégzett munka), tartalékként cím+szerző egyeztetéssel a
  fájlnévből (ékezet- és írásjel-független normalizálással, a moly-os
  címekben lévő láthatatlan U+200B karaktereket is kezelve).
- A 152 MB-os adatbázist **nem másoljuk be** az appba, helyben, csak
  olvasásra nyitjuk meg.
