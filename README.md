# Vox Libris

**Helyi e-könyvtár böngésző és felolvasó (TTS) Androidra — nagyon nagy könyvtárakhoz.**

🇭🇺 Magyar (ez a lap) · 🇬🇧 [English](README.en.md)

🔧 [Telepítés és függőségek](docs/TELEPITES.md) · 📖 [Használati útmutató](docs/HASZNALAT.md) · 📝 [Változásnapló](CHANGELOG.md)

---

Natív Android alkalmazás (Kotlin + Jetpack Compose), amely egy helyi SQLite
katalógushoz (~68 000 könyv metaadata) párosítja a telefonon lévő
könyvfájlokat, és a rendszer szövegfelolvasójával (TTS) hangoskönyvként
olvassa fel őket — mondatról mondatra, automatikus folytatással,
könyvjelzőkkel és fülhallgató-gombos vezérléssel.

Borítóképekkel **szándékosan nem foglalkozik**: se külső mappából, se a
könyvfájlok belsejéből nem olvas be képet. Ettől marad villámgyors a
görgetés 70 000+ rekord mellett is, és nem eszi a memóriát.

Az alkalmazás a telefonon **Könyvtár TTS** néven jelenik meg.

## Gesztusok röviden

| Gesztus | Fájlböngészőben | Olvasó képernyőn |
|---|---|---|
| **Szimpla koppintás** | mappa megnyitása / könyv részletei | — |
| **Dupla koppintás** | felolvasás az utolsó pozíciótól | felolvasás **a megérintett mondattól** |
| **Hosszú nyomás** | olvasó megnyitása | könyvjelző a bekezdéshez |

## Fő funkciók

- **Katalógus-integráció.** A `konyvek` tábla metaadatai (cím, szerző,
  leírás, kiadó, címkék…), és a `fizikai_fajlok` táblában a PC-n már
  elvégzett fájl↔könyv párosítás újrafelhasználása — így a telefonnak alig
  kell dolgoznia.
- **Katalógus építése a semmiből.** Ha nincs kész adatbázisod, az app maga
  készít egyet **a könyvfájlok saját metaadataiból** (EPUB/FB2/MOBI/DOCX
  fejlécekből: cím, szerző, fülszöveg, kiadó, sorozat, ISBN, címkék) —
  internet nélkül. Újrafuttatva **inkrementális**: a meglévő bejegyzéseket
  érintetlenül hagyja, csak az újonnan bemásolt könyveket veszi fel.
- **Total Commander-stílusú böngésző.** Sűrű, ikonmentes lista (név, méret,
  dátum, párosított szerző/cím), mappánkénti navigáció, rekurzív szkennelés,
  oszlopfejlécre koppintva rendezés, gyorsgörgető sáv, tárolóváltó
  (belső tároló ⇄ SD-kártya ⇄ USB).
- **Egyesített olvasó + lejátszó képernyő.** A szöveg és minden
  felolvasás-vezérlő egy helyen. Alsó sáv: fejezet ⏫⏬, képernyőnyi lapozás
  ⬆⬇, mondatléptetés ⏮⏭, Play/Pause. Mellette betűméret, pozíció-csúszka,
  **követés** (a szöveg magától gördül a felolvasott résszel) és **hangolás**
  (sebesség, hangmagasság). Fent: keresés, könyvjelző, könyvjelzőlista, Stop.
- **Mondatszintű felolvasás.** A TTS mondatonként halad; az éppen felolvasott
  mondat kiemelve látszik; a mentett pozíció is mondatpontos, így a
  folytatás mindig ott veszi fel a fonalat, ahol abbahagytad.
- **Keresés a szövegben.** Ékezet-független (a *varazslono* megtalálja a
  *varázslónő*-t), a találatok kiemelve, ▲▼ gombokkal ugrálva, számlálóval.
- **Könyvjelzők.** Hosszú nyomásra bárhol; lista, ugrás, törlés. A lejátszás
  közben letett jelző a felolvasott helyre kerül.
- **Fülhallgató-gombok** (Bluetooth és vezetékes, MediaSession-en át):
  1 nyomás = Start/Stop, 2 nyomás = ~5 másodperc vissza.
- **Olvasási lista + statisztika.** Külön kategóriában az **elolvasott** és a
  **folyamatban lévő** könyvek, haladás-csíkkal, hallgatási idővel, utolsó
  dátummal.
- **Exportálás.** Az olvasási nyilvántartás CSV-be és SQLite-másolatba a
  `Download/KonyvtarTTS/` mappába (PC-re másoláshoz), vagy megosztás
  e-mailben/felhőbe.
- **Külső TTS.** A kinyert teljes szöveg átadható más felolvasó
  alkalmazásnak (`ACTION_SEND`).

## Formátumtámogatás

| Formátum | Állapot |
|---|---|
| epub, txt, fb2, htm/html | teljes |
| mobi, prc, azw, azw3 | teljes (PalmDOC kitömörítés; DRM-es és HUFF/CDIC fájlnál érthető hibaüzenet) |
| rtf | teljes (Windows-1250 kódlappal is) |
| pdf | szövegréteg kinyerése (szkennelt PDF-nél hibaüzenet — OCR után .txt-ként felolvasható) |
| docx | teljes |
| doc, djvu | csak listázás és párosítás — felolvasáshoz konvertálás kell (pl. Calibre) |

Minden formátumot **saját, függőségmentes olvasó** dolgoz fel (egyedül a PDF
használ külső könyvtárat, a PDFBox-Androidot).

## Az adatbázis, amit vár

Az app egy tetszőleges SQLite fájlt nyit meg **csak olvasásra**. A minimum,
amire szüksége van:

```sql
CREATE TABLE konyvek (
    id           INTEGER PRIMARY KEY,
    szerzo       TEXT,
    cim          TEXT,
    leiras       TEXT,      -- fülszöveg / szinopszis
    kiado        TEXT,
    kiadas_eve   TEXT,
    isbn         TEXT,
    sorozat      TEXT,
    sorozat_szama TEXT,
    cimkek       TEXT,
    formatum     TEXT,
    meret        TEXT,
    ncore_id     TEXT,
    feltoltve_datum TEXT
);

-- Opcionális, de nagyon gyorsítja a párosítást:
-- a PC-n már elvégzett fájlnév → könyv hozzárendelés
CREATE TABLE fizikai_fajlok (
    fajl_nev  TEXT,
    konyv_id  INTEGER REFERENCES konyvek(id)
);
```

Ha a `fizikai_fajlok` tábla hiányzik vagy üres, az app a fájlnevekből
próbál cím + szerző alapján párosítani (ékezet- és írásjel-független
normalizálással).

## Építés

Előfeltétel: Android Studio, vagy csak Android SDK + JDK 17.

```bash
gradlew.bat assembleRelease
```

Kimenet: `app/build/outputs/apk/release/app-release.apk` (~9 MB, debug
kulccsal aláírva, azonnal telepíthető). Android Studióból: nyisd meg a
mappát, majd Run ▶.

Minimum Android 11 (API 30), cél: Android 15 (API 35).
AGP 8.11.1 · Kotlin 2.2.0 · Gradle 8.13 · Compose BOM 2025.01.00

## Telepítés és első indítás

1. Másold a telefonra az `app-release.apk`-t és telepítsd (az „ismeretlen
   forrás” engedélyezése kellhet).
2. Másold a telefonra a katalógus `.db` fájlt — **csak magát a `.db`-t**, a
   `-wal` és `-shm` fájlokat ne. Javasolt hely: a belső tároló gyökere vagy a
   `Download` mappa (itt az app magától megtalálja `ncore_konyvtar.db` néven);
   máshonnan a Beállítások → „Adatbázisfájl kiválasztása…” alatt tallózható.
   Belső tárolóra tedd, ne SD-kártyára: az SQLite sok apró olvasást végez,
   és ez a belső tárolón lényegesen gyorsabb.
3. Másold fel a könyvfájlokat egy tetszőleges mappába (ezek mehetnek
   SD-kártyára is).
4. Első indításkor add meg a **„Minden fájl kezelése”** engedélyt (a gomb a
   rendszerbeállításokba visz), Android 13+ esetén az értesítési engedélyt is.
5. Navigálj a könyves mappádba, és nyomd meg a **radar ikont** — ez rekurzívan
   végigszkennel mindent és párosít. Utána a **„Katalógus”** nézetben az
   összes talált fájl egyben, kereshetően látszik.

**Kell egy szövegfelolvasó motor is** (magyar hanggal), ez nem az app része,
hanem a rendszeré — a telepítése, a hangok letöltése és a Bluetooth-os
fülhallgatók tudnivalói itt vannak leírva:
**[docs/TELEPITES.md](docs/TELEPITES.md)**

Részletes leírás minden képernyőről: [docs/HASZNALAT.md](docs/HASZNALAT.md)

## Architektúra

```
app/src/main/java/hu/konyvtar/tts/
├── MainActivity.kt        – navigáció, engedély-képernyő
├── App.kt                 – app-szintű inicializálás
├── model/Models.kt        – adatosztályok, haladásszámítás
├── data/
│   ├── CatalogDb.kt       – a külső katalógus (csak olvasás) + CatalogHolder
│   ├── AppDb.kt           – saját DB: szkennelési cache, pozíciók, könyvjelzők
│   ├── Matcher.kt         – fájlnév → könyv párosítás, szövegnormalizálás
│   ├── FileScanner.kt     – rekurzív szkennelés, inkrementális cache
│   ├── Exporter.kt        – CSV + SQLite export a Letöltések mappába
│   └── Prefs.kt           – beállítások
├── reader/                – szövegkinyerés formátumonként
│   ├── TextExtractor.kt   – egységes belépési pont, fejezetek, szöveg-cache
│   └── Sentences.kt       – magyar mondathatár-felismerés
├── tts/TtsService.kt      – előtér-szolgáltatás: TTS, pozíciómentés, MediaSession
└── ui/                    – Compose képernyők (Explorer, Detail, Reader,
                             Stats, Settings, FilePicker)
```

Fontosabb tervezési döntések:

- **Az olvasási pozíciók az app saját adatbázisában** vannak, nem a
  katalógusban — így a katalógus frissítése (újramásolása a PC-ről) sosem
  törli a haladást.
- A **kinyert szöveg gyorsítótárba** kerül (`cacheDir/text/`), ezért az első
  megnyitás után a folytatás azonnali. A fejezethatárok külön kis fájlba
  mentődnek a szöveg mellé.
- A párosítás elsődlegesen `fizikai_fajlok.fajl_nev` alapján történik (a PC-n
  már elvégzett munka újrahasznosítása), tartalékként cím + szerző
  egyeztetéssel a fájlnévből — a moly.hu-s címekben előforduló láthatatlan
  U+200B karaktereket is kezelve.
- A nagy katalógust **nem másoljuk be** az appba: helyben, csak olvasásra
  nyitjuk meg.
- A böngészőlista **SQL-ből** rendez és szűr, nem memóriában — ezért marad
  gyors több tízezer sornál is.

## Adatvédelem

Az alkalmazás **nem kér internet-hozzáférést** (nincs `INTERNET` engedély a
manifestben), tehát technikailag képtelen adatot küldeni bárhová. Minden
adat — a katalógus, a könyvek, az olvasási pozíciók, a könyvjelzők — a
telefonon marad. Nincs analitika, nincs hirdetés, nincs felhasználói fiók.

## Licenc

MIT — lásd a [LICENSE](LICENSE) fájlt. Szabadon használható, módosítható és
terjeszthető, a szerzői jogi megjegyzés megtartásával.

## Állapot

Személyes projekt, saját napi használatra készült, de bárki használhatja
hasonló célra. Hibajelzést és ötletet szívesen fogadok az Issues-ban.

## Köszönet

- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) — PDF
  szövegréteg kinyerése
- Az app a rendszer TTS motorját használja (magyar hanghoz pl. a Google
  Szövegfelolvasó ajánlott)
