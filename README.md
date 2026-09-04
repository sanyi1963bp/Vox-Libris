# Vox Libris

**Helyi e-könyvtár és felolvasó (TTS) Androidra — nagyon nagy könyvtárakhoz.**

🇭🇺 Magyar (ez a lap) · 🇬🇧 [English](README.en.md)

**A felület tíz nyelven**: magyar · angol · német · francia · spanyol ·
portugál · lengyel · cseh · szlovák · orosz

📱 [Letöltés és tesztelői útmutató](https://sanyi1963bp.github.io/Vox-Libris/docs/) ·
🔧 [Telepítés](docs/TELEPITES.md) · 📖 [Használat](docs/HASZNALAT.md) ·
🗺 [Fejlesztési terv](docs/TERV.md) · 📝 [Változásnapló](CHANGELOG.md)

---

Natív Android alkalmazás (Kotlin + Jetpack Compose). A telefonodon lévő
könyvekből **maga építi a katalógusát** — a fájlokban tárolt metaadatokból,
internet nélkül —, majd a rendszer szövegfelolvasójával (TTS) hangoskönyvként
olvassa fel őket: mondatról mondatra, automatikus folytatással,
könyvjelzőkkel és fülhallgató-gombos vezérléssel.

Nem kell hozzá se fiók, se szerver, se előre elkészített adatbázis: telepíted,
megmutatod, hol vannak a könyveid, és kész.

Az alkalmazás a telefonon **Könyvtár TTS** néven jelenik meg.

## A két nézet

A nyitóképernyő a **könyvtár**: az összes könyved egy listában, kereséssel,
betűsávval és formátumszűrővel. Jobbra-balra **pöccintve** átvált a
**fájlböngészőre**, ahol mappák szerint nézheted ugyanezt. A cím melletti két
pötty mutatja, melyiknél állsz.

Ezen kívül van egy **polc** is (borítókat lapozva, a felső sáv ikonjáról), és
egy **olvasó képernyő**, ahol a szöveg és minden vezérlő együtt van.

**Minden nézet alján ott egy sáv** három gombbal — **Könyvtár · Fájlok ·
Olvasó** —, tehát bárhonnan egy koppintással bárhová. Az aktív nézet ki van
emelve, így az is látszik, hol vagy. Fölötte ül a „most szól" csík, ha van
betöltött könyv.

## Gesztusok röviden

| Gesztus | Könyvtárban és böngészőben | Olvasó képernyőn |
|---|---|---|
| **Szimpla koppintás** | kijelölés / mappa megnyitása | — |
| **Dupla koppintás** | megnyitás **és felolvasás** az utolsó pozíciótól | felolvasás **a megérintett mondattól** |
| **Hosszú nyomás** | helyi menü (adatlap, jegyzet, fájlműveletek) | műveletmenü a megérintett mondatra |

Az olvasóban a hosszú nyomás **kapcsolható**: ha inkább azonnal könyvjelzőt
tenne, a beállításokban átállítható, és akkor a műveletmenü egyszeri
koppintásra jön elő.

## Fő funkciók

- **Saját katalógus, internet nélkül.** Az app végigjárja a könyvmappádat, és
  **a fájlok saját metaadataiból** épít katalógust (EPUB/FB2/MOBI/DOCX
  fejlécekből: cím, szerző, fülszöveg, kiadó, sorozat, ISBN, címkék).
  Újrafuttatva **inkrementális**: a meglévő bejegyzéseket érintetlenül hagyja,
  csak az újonnan bemásolt könyveket veszi fel. A katalógus látható fájl
  (`Download/KonyvtarTTS/sajat_katalogus.db`), így túléli az app
  újratelepítését, és PC-n is megnyitható.
- **Keresés, ami tényleg megtalál.** A kereső egyszerre nézi a **címet, a
  szerzőt, a fájlnevet és a saját jegyzeteidet**, ékezettől függetlenül (a
  *jozsef* megtalálja a *József*-et). A szűrés memóriában fut, ezért gépelés
  közben több ezer könyvnél is azonnal frissül.
- **Betűsáv**, ami csak azokat a kezdőbetűket mutatja, amikhez tényleg van
  könyved — nincs üresbe vezető gomb. A rendezéstől függően a cím vagy a
  szerző kezdőbetűjét nézi.
- **Valódi borítók.** Kinyeri őket magukból a könyvfájlokból: EPUB (az OPF
  háromféle jelölése), MOBI/AZW3 (EXTH-rekord), FB2 (base64), PDF (az első
  oldal kirajzolása). Kicsinyítve, WebP-ben tárolja (~20 KB/db), és a
  beolvasás **második, háttérben futó menetében** töltődnek, hogy a lista
  addig is használható legyen. Ahol nincs borító, a címből és a szerzőből
  rajzol egyet.
- **Formátum egy pillantásra.** Minden soron ott a formátumjelvény; ami nem
  olvasható fel, az **szürke**. A formátumszűrő darabszámmal sorolja fel,
  miből mennyi van a telefonon.
- **Fájlműveletek minden nézetből.** Átnevezés, áthelyezés, másolás, törlés —
  és **minden hozzá kötött adat követi a fájlt**: a katalógusbejegyzés, az
  olvasási haladás, a könyvjelzők, a jegyzet és a bélyegkép. Fájlkezelőben
  elvégezve mindez csendben elveszne.
- **Saját jegyzetek.** Bármit hozzáfűzhetsz egy könyvhöz; a listában jel
  mutatja, melyikhez van, és a kereső a jegyzetekben is keres.
- **Egyetlen könyv-képernyő.** A szöveg és minden vezérlő egy helyen —
  nincs külön lejátszó- vagy részletező-ablak. Alul egy sorban:
  **fejezet ◀ · bekezdés ◀ · mondat ◀ · ▶/⏸ · mondat ▶ · bekezdés ▶ ·
  fejezet ▶**. Mellette pozíció-csúszka, betűméret, **követés** (a szöveg
  magától gördül a felolvasott résszel) és **hangolás** (sebesség,
  hangmagasság).
- **„Most szól" sáv** minden képernyő alján: látod, melyik könyv szól és hol
  tart, egy koppintással visszaugrasz hozzá, a gombbal bárhonnan elnémítható.
- **Mondatszintű felolvasás.** A TTS mondatonként halad; az éppen felolvasott
  mondat kiemelve látszik; a mentett pozíció is mondatpontos, így a
  folytatás mindig ott veszi fel a fonalat, ahol abbahagytad.
- **Hangjelzés a fejezetek előtt** — mélyebb kettős hang, külön kapcsolható,
  állítható hangerővel. A fejezethatárokat a szövegben **vérvörös sáv** is
  jelzi.
- **Keresés a szövegben.** Ékezet-független, a találatok kiemelve, ▲▼
  gombokkal ugrálva, számlálóval.
- **Könyvjelzők.** A szöveg műveletmenüjéből bárhol; lista, ugrás, törlés.
- **Kiejtési szótár.** A gépi hang a kitalált és az idegen neveket rendre
  elrontja. Hosszú nyomás a mondaton → **Kiejtés** → beírod, hogyan mondja,
  és onnantól **minden könyvben jól mondja**; ha épp szól a felolvasás, a
  mondatot rögtön újra is mondja. A csere a szó elejéhez kötött, de a
  végződést nem bántja, így a `Bree` szabály a „Breeben" alakot is eltalálja.
- **Műveletmenü a szövegen**: *Könyvjelző · Kiejtés · Wikipédia ·
  Idézetkártya · Másolás*. A **megérintett mondattal** dolgozik, és meg is
  mutatja, melyikkel — ugyanazzal a darabbal, amit a felolvasó egy egységként
  mond ki. A **Wikipédia** a szót átadja a böngészőnek, tehát az app maga
  továbbra sem megy internetre. Az **idézetkártya** a mondatból megosztható
  képet rajzol a futó színsémával.
- **Bionic Reading.** Minden szó első ~40%-a félkövér, hogy a szem a
  szókezdetekbe kapaszkodhasson. Kapcsolható, az olvasó hangolósávjában.
- **„Hol voltam?"** A legutóbb hallgatott rész négy legjellemzőbb mondata,
  eredeti sorrendben. Nem összefoglaló: a könyv saját mondatai. Aki a szemével
  olvas, visszalapoz egy oldalt; aki hallgat, nem tud.
- **Szereplők.** Kik tűntek fel eddig, gyakoriság szerint, mindegyikhez az
  első előfordulás mondatával és a leggyakoribb társaival. Egy névre koppintva
  odaugrik.
  - **Részletes leírások**, ha a könyv mellett van egy `.vox.json` fájl. Ezt a
    **saját géped készíti**, egy ott futó nyelvi modellel
    (`tools/vox_characters.py`) — az app csak beolvassa, tehát **továbbra sem
    megy internetre**. Lásd lentebb.
  - Mindkettő **csak az eddig olvasott részt nézi**, tehát spoilermentes, és
    mindkettő **helyben fut, mesterséges intelligencia nélkül**. A mondatokat
    TF-IDF-fel pontozzuk, a korpusz maga a könyv olvasott része — így nincs
    szükség kötőszólistára, és bármelyik nyelven működik.
- **Fülhallgató-gombok** (Bluetooth és vezetékes, MediaSession-en át):
  1 nyomás = Start/Stop, 2 nyomás = ~5 másodperc vissza.
- **Megjelenés.** Világos/sötét téma vagy rendszerkövetés, **hat színséma**
  (klasszikus zöld, tenger kék, szépia, naplemente, éjszakai, magas
  kontraszt), és külön állítható betűméret a felülethez és a könyvhöz.
- **Tíz nyelv.** A teljes felület lefordítva, és **a felület nyelve külön
  állítható a felolvasás nyelvétől**. Új nyelv hozzáadása egyetlen XML-fájl,
  kódolás nélkül.
- **Olvasási lista + statisztika.** Külön kategóriában az **elolvasott** és a
  **folyamatban lévő** könyvek, haladás-csíkkal, hallgatási idővel, utolsó
  dátummal.
- **Exportálás.** Az olvasási nyilvántartás CSV-be és SQLite-másolatba a
  `Download/KonyvtarTTS/` mappába, vagy megosztás e-mailben/felhőbe.

## Formátumtámogatás

| Formátum | Felolvasás | Borító |
|---|---|---|
| epub | teljes | igen |
| fb2 | teljes | igen |
| mobi, prc, azw, azw3 | teljes (PalmDOC kitömörítés; DRM-es és HUFF/CDIC fájlnál érthető hibaüzenet) | igen |
| pdf | szövegréteg kinyerése (szkennelt PDF-nél hibaüzenet — OCR után .txt-ként felolvasható) | az első oldal |
| txt, htm/html | teljes | nincs |
| rtf | teljes (Windows-1250 kódlappal is) | nincs |
| docx | teljes | nincs |
| doc, djvu | nem — konvertálás kell hozzá (pl. Calibre) | nincs |

Minden formátumot **saját, függőségmentes olvasó** dolgoz fel (egyedül a PDF
használ külső könyvtárat, a PDFBox-Androidot).

## Telepítés

A legegyszerűbb út a **[letöltőoldal](https://sanyi1963bp.github.io/Vox-Libris/docs/)**:
telefonról megnyitva egy gombbal települ, és minden tudnivaló rajta van.

Kézzel:

1. Töltsd le a legfrissebb APK-t a
   [Releases](https://github.com/sanyi1963bp/Vox-Libris/releases) oldalról, és
   telepítsd (az „ismeretlen forrás" engedélyezése kellhet).
2. Első indításkor add meg a **„Minden fájl kezelése"** engedélyt (a gomb a
   rendszerbeállításokba visz), Android 13+ esetén az értesítési engedélyt is.
3. Az app megkérdezi, **hol vannak a könyveid** — válaszd ki a mappát.
4. Utána felajánlja, hogy **beolvassa őket**. Egy gomb, és elkészül a
   katalógus; a borítók a háttérben töltődnek utána.

Új könyvek bemásolása után elég újra elindítani a beolvasást
(Beállítások → Katalógus): a meglévő bejegyzésekhez nem nyúl, csak az újakat
veszi fel.

**Kell egy szövegfelolvasó motor is** (magyar hanggal), ez nem az app része,
hanem a rendszeré — a telepítése, a hangok letöltése és a Bluetooth-os
fülhallgatók tudnivalói itt vannak leírva:
**[docs/TELEPITES.md](docs/TELEPITES.md)**

Részletes leírás minden képernyőről: [docs/HASZNALAT.md](docs/HASZNALAT.md)

## Szereplőleírások a géped segítségével

Az app nem tudja megmondani, ki kicsoda egy regényben — ahhoz valódi
szövegértés kell, és ezt [megmértük](CHANGELOG.md): szabályokkal huszonöt
szereplőből egyre ment, hibásan.

Ehelyett a nehéz munkát **a számítógéped végzi**, és az eredmény egy kis fájl
a könyv mellett. Az alkalmazás csak beolvassa, tehát **változatlanul nincs
internet-engedélye**.

Kell hozzá [Ollama](https://ollama.com) és egy letöltött modell:

```bash
ollama pull qwen2.5:14b
```

Aztán egy könyvre vagy egy egész mappára:

```bash
python tools/vox_characters.py "D:/konyvek/A kiraly.epub"
```

```bash
python tools/vox_characters.py "D:/konyvek" --all
```

Az eszköz adagokban végigolvastatja a könyvet a modellel, összevonja a
jegyzeteket, és `A kiraly.vox.json` néven a könyv mellé írja. Ezt a fájlt a
könyvvel együtt másold a telefonra.

A formátumot a [tools/pelda.vox.json](tools/pelda.vox.json) mutatja. A leírás
a **teljes könyvet** ismeri, tehát elárulhat későbbi fejleményeket is.

Támogatott bemenet: `epub`, `txt`, `html`.

## Építés

Előfeltétel: Android Studio, vagy csak Android SDK + JDK 17.

```bash
gradlew.bat assembleRelease
```

Kimenet: `app/build/outputs/apk/release/app-release.apk` (~10 MB, debug
kulccsal aláírva, azonnal telepíthető). Android Studióból: nyisd meg a
mappát, majd Run ▶.

Az egységtesztek emulátor nélkül futnak:

```bash
gradlew.bat testDebugUnitTest
```

Minimum Android 11 (API 30), cél: Android 15 (API 35).
AGP 8.11.1 · Kotlin 2.2.0 · Gradle 8.13 · Compose BOM 2025.01.00

## Architektúra

```
app/src/main/java/hu/konyvtar/tts/
├── MainActivity.kt            – navigáció, a lapozható főképernyő, engedélykérés
├── App.kt                     – app-szintű inicializálás
├── model/Models.kt            – adatosztályok, haladásszámítás
├── data/
│   ├── Catalog.kt             – a saját katalógus (látható .db a Letöltésekben)
│   ├── AppDb.kt               – pozíciók, könyvjelzők, jegyzetek
│   ├── LibraryScanner.kt      – beolvasás: metaadat + katalógusba írás
│   ├── MetadataExtractor.kt   – cím, szerző, fülszöveg a fájlokból
│   ├── CoverExtractor.kt      – borítókép kinyerése formátumonként
│   ├── CoverStore.kt          – bélyegkép-tár (WebP + memória-gyorstár)
│   ├── CoverScanner.kt        – a borítók háttérben futó második menete
│   ├── FileOps.kt             – átnevezés, áthelyezés, másolás, törlés
│   ├── Pronounce.kt           – kiejtési szótár: átírások a felolvasónak
│   ├── QuoteCard.kt           – idézetkártya rajzolása és megosztása
│   ├── Normalizer.kt          – szövegnormalizálás, ékezetfolding
│   ├── Exporter.kt            – CSV + SQLite export a Letöltések mappába
│   └── Prefs.kt               – beállítások
├── reader/                    – szövegkinyerés formátumonként
│   ├── TextExtractor.kt       – egységes belépési pont, fejezetek, szöveg-cache
│   ├── Sentences.kt           – mondathatárok, szóválasztás a menühöz
│   ├── Bionic.kt              – a félkövéren szedendő szókezdetek
│   └── XmlReader.kt           – Android-független XML, hogy tesztelhető legyen
├── tts/TtsService.kt          – előtér-szolgáltatás: TTS, pozíció, MediaSession
├── vm/
│   ├── LibraryViewModel.kt    – katalógus, szűrt lista, polc, beolvasás
│   └── BrowserViewModel.kt    – a fájlrendszer böngészése
└── ui/                        – Compose képernyők
    ├── LibraryScreen.kt       – a könyvtár listája (nyitóképernyő)
    ├── ExplorerScreen.kt      – fájlböngésző
    ├── ShelfScreen.kt         – lapozható borítónézet
    ├── ReaderScreen.kt        – állapot és huzalozás; a megjelenítés külön:
    │                            ReaderTopBar, ReaderControls, ReaderText,
    │                            BookmarksDialog
    ├── BookDetails.kt         – a könyv adatlapja (mindhárom nézetnek)
    ├── FileActions.kt         – a helyi menü és ablakai
    ├── ReaderActions.kt       – a szöveg műveletmenüje, szóválasztó, kiejtés
    ├── PronounceCard.kt       – a kiejtési szótár a beállításokban
    └── …                      – Settings, Stats, FilePicker, NowPlayingBar

app/src/test/java/hu/konyvtar/tts/   – 79 egységteszt, emulátor nélkül
```

Fontosabb tervezési döntések:

- **Az olvasási pozíciók az app saját adatbázisában** vannak, nem a
  katalógusban — így a katalógus újraépítése sosem törli a haladást.
- A **katalógus szándékosan látható fájl** a Letöltések mappában, nem az app
  rejtett tárhelyén: túléli az újratelepítést, és PC-n is megnyitható.
- A **lista memóriában szűr**, nem SQL-ből: a katalógus egyszer betöltődik,
  előre elkészített, ékezet nélküli kulcsokkal — ezért marad azonnali a
  gépelés közbeni keresés több ezer könyvnél is.
- A **borítók külön menetben** töltődnek. Egy borító kinyerése sokkal drágább,
  mint a metaadaté (kép dekódolása, PDF-oldal kirajzolása), ezért nem
  várakoztatjuk vele a listát.
- A **kinyert szöveg gyorsítótárba** kerül (`cacheDir/text/`), ezért az első
  megnyitás után a folytatás azonnali. A fejezethatárok külön kis fájlba
  mentődnek a szöveg mellé.
- A **fájlműveletek átvezetik a hivatkozásokat.** Ez a lényegük: a katalógus,
  a haladás, a könyvjelzők, a jegyzet és a bélyegkép mind követi a fájlt.
- A **parserek nem függenek az Androidtól** (saját `XmlReader`), ezért sima
  JUnit-tal tesztelhetők — ott a legveszélyesebb a hiba, mert nem omlik össze
  semmi, csak rosszul lesz felolvasva egy könyv.

## Adatvédelem

Az alkalmazás **nem kér internet-hozzáférést** (nincs `INTERNET` engedély a
manifestben), tehát technikailag képtelen adatot küldeni bárhová. Minden
adat — a katalógus, a könyvek, az olvasási pozíciók, a könyvjelzők, a
jegyzetek — a telefonon marad. Nincs analitika, nincs hirdetés, nincs
felhasználói fiók.

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
