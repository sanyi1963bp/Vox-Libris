# Változásnapló / Changelog

🇭🇺 [Magyar](#magyar) · 🇬🇧 [English](#english)

---

## Magyar

A formátum a [Keep a Changelog](https://keepachangelog.com/hu/1.0.0/) ajánlást
követi, a verziószámozás a [SemVer](https://semver.org/lang/hu/) szerint megy.

### [4.6.0] — 2026-09-04

**A szereplőlista két valódi regényen mérve**

Megpróbáltuk kiolvasni azt is, hogy **ki kicsoda** — a szövegben álló
közbevetésekből és rokonsági fordulatokból.

**Ez megbukott, és ezért nem került bele.** Két valódi regényen mérve — egy
mai fordításon és egy Thackerayen — huszonöt szereplőből egyre adott
bemutatást, és az is hibás volt. Tanulságos, min:

- A vessző nem csak bemutatást vezet be. Megszólítást is („Pantaleon, hozd
  be a konyakot") és határozói mellékmondatot is.
- A rokonsági fordulatnál a mondatban két név áll, és nyelvtani elemzés
  nélkül nem eldönthető, melyikről szól: a listára egyszerre került fel a
  „Naum → Bernsztajn fia" és a „Bernsztajn → Naum fia" — az apa-fiú viszony
  **megfordítva is**.
- A „Fiat" autómárka rokonsági szónak látszott, mert a `fia` tő és a `t` rag
  ráillik.

Egy rossz bemutatás rosszabb, mint a semmilyen: az utóbbi hallgat, az előbbi
félrevezet. Ehhez a feladathoz valódi szövegértés kell.

**Ami viszont bekerült, mert a mérés szerint működik:**

- **Kikkel szerepel együtt.** Minden szereplőnél ott a három leggyakoribb
  társa. Ez adta a leghasznosabb jelzést: a próbakönyvben rögtön látszott,
  ki tartozik kihez.
- **A megjelenített név a leggyakoribb alak, nem a legrövidebb.** Enélkül a
  „Newcome" 457 említése **„New" néven** jelent meg, mert a „New" önállóan is
  előfordul („New Street"), és rövidebb lévén ő lett a tő. Ugyanígy „Eth" az
  Ethel és „Hob" a Hobson helyett.
- **A hosszú magánhangzós rag is összevonódik.** A „Szapiro" és a „Szapirót"
  külön szereplőként szerepelt, 493 és 44 említéssel — ugyanaz az ember,
  kettévágva.

A névfelismerés és a sebesség jónak bizonyult: egy 615 ezer karakteres
regényen 150 ms, és a lista élén tényleg a főszereplők álltak.

Ami továbbra sem megy: a **helyneveket** nem különbözteti meg a
személynevektől (Varsó, London, Palesztina a listán van), és a **címeket**
sem (Sir, Lady, Lord).

### [4.5.0] — 2026-09-04

**Tudás a könyvről: „Hol voltam?" és karakternévtár**

Mindkettő az olvasó **⋮ menüjéből** érhető el, és **egyik sem néz túl az
olvasási pozíciódon**. Ez nem óvatoskodás: a fejezet hátralévő része spoiler,
és azt nem lehet visszacsinálni.

- **„Hol voltam?"** — a legutóbb hallgatott rész **négy legjellemzőbb
  mondata**, eredeti sorrendben. Nem összefoglaló, és nem meséli el a
  történetet: a könyv saját mondatai. Aki a szemével olvas, visszalapoz egy
  oldalt; aki hallgat, nem tud — ez pótolja azt.
- **Szereplők** — kik tűntek fel eddig, gyakoriság szerint, mindegyikhez az
  első előfordulás mondatával. Egy névre koppintva odaugrik ahhoz a
  bekezdéshez.

Mindkettő **helyben fut, mesterséges intelligencia és internet nélkül** — az
alkalmazásnak továbbra sincs internet-engedélye.

Ami a motorháztető alatt van:

- A mondatok pontozása **TF-IDF**, ahol a korpusz maga az eddig olvasott
  szöveg. Ami minden bekezdésben ott van, az nulla súlyt kap — a névelő és a
  kötőszó magától kiesik. **Nincs kötőszólista, amit tíz nyelvre kellene
  karbantartani**, és bármelyik nyelvű könyvön működik.
- A névfelismerés is szótár nélküli: ami nagybetűs, és **nem csak mondat
  elején** áll, az név.
- A ragozott alakok tőre vonva egyesülnek: a „Gandalfot", „Gandalfnak",
  „Gandalffal" a „Gandalf" alá kerül. A magyar **tővégi magánhangzó-nyúlást**
  is kezeli (`Anna → Annát`), enélkül minden `-a` és `-e` végű név kimaradt
  volna.
- 18 új egységteszt, köztük olyanok, amik kifejezetten azt őrzik, hogy egyik
  funkció se nézzen a pozíción túlra. Összesen 97 teszt fut.

Ez nem tökéletes: a főszereplőknél megbízhatóan működik, ritkább neveknél
elmaradhat egy-egy ragozott alak.

### [4.4.0] — 2026-09-02

**Állandó navigációs sáv minden nézetben**

- **Alsó sáv mindenhol**, három gombbal: **Könyvtár · Fájlok · Olvasó**.
  Bármelyik nézetből egy koppintással bármelyik másikra. Az éppen aktív
  nézet ki van emelve, tehát az is látszik, hol vagy — nem csak az, hova
  mehetsz.
- **Az Olvasó gomb akkor is él, ha épp nem szól semmi**: ilyenkor a
  legutóbb hallgatott könyvre ugrik. Csak akkor halvány, ha még egyetlen
  könyvet sem nyitottál meg.
- **Az olvasóba érkezve az aktuális mondat pár másodpercig erősebben
  világít**, aztán visszahalványul a szokásos kiemelésre. Az alsó sávból
  bármikor ide lehet ugrani, és ilyenkor az első kérdés mindig ugyanaz:
  „hol is tartunk?" — ez egy pillantással megválaszolja.
- A **„most szól" csík** nem külön sáv többé: közvetlenül a navigáció
  fölött ül, egy egységként. Két külön sáv egymáson elvette volna a helyet
  a szövegtől, pont az olvasóban.
- A pöccintés a könyvtár és a fájlböngésző között **változatlanul működik**;
  a sáv gombjai ugyanarra a lapozóra hatnak, tehát a kettő nem üti egymást.
- A **mappaválasztóból kimarad** a sáv, és ez szándékos: az egy befejezendő
  művelet, nem nézet.

### [4.3.1] — 2026-09-02

**Fülhallgató-gombok: négy hiba javítva**

Éjszakai tesztelésen derült ki, hogy a Bluetooth-fülhallgató gombjai csak
akkor működtek, ha az app a képernyőn volt, és minden más lejátszó ki volt
lőve. Négy külön hiba játszott össze; mind javítva:

- **Szünetben visszaadtuk a hangfókuszt.** A rendszer a fülhallgató-gombokat
  ahhoz az apphoz irányítja, aki a fókuszt tartja; aki elengedi, azt kihagyja
  az osztásból. Ezért indított el a következő gombnyomás egy másik lejátszót.
  Mostantól a fókusz szünetben is nálunk marad, és csak teljes leállításkor
  kerül vissza.
- **Szünetben kiléptünk az előtérből.** Emiatt a rendszer bármikor eldobhatta
  a szolgáltatást — és vele a MediaSessiont, aminek a gombokat kézbesíteni
  kellett volna. Innen jött az, hogy „csak akkor működik, ha a képernyőn van".
  Mostantól betöltött könyv mellett előtérben maradunk; a leállítás az
  értesítés gombjával történik.
- **Az átmeneti fókuszvesztést véglegesnek vettük.** Egyetlen értesítéshang
  megállította a felolvasást, és onnantól magától nem indult újra. Most
  megkülönböztetjük a kettőt: átmeneti veszteség után **magától folytatja**,
  amint visszakapja a hangot. Beszédnél a halkítás nem járható út, ezért
  ilyenkor is szünetelünk — de már visszatérünk belőle.
- **Hiányzott a `MediaButtonReceiver`.** Ha a szolgáltatás mégis leállt, a
  gombnyomásnak nem volt hová megérkeznie. Most a rendszer fel tudja
  ébreszteni vele az appot.

### [4.3.0] — 2026-09-01

**Műveletmenü a szövegen, és kiejtési szótár**

- **Műveletmenü a könyv szövegén**: *Könyvjelző · Kiejtés · Wikipédia ·
  Idézetkártya · Másolás*. A menü a **megérintett mondattal** dolgozik, és
  meg is mutatja, melyikkel — ugyanazzal a darabbal, amit a felolvasó is
  egy egységként mond ki.
- **Kiejtési szótár.** Ha a hang elrontja egy név kiejtését: hosszú nyomás
  → *Kiejtés* → beírod, hogyan mondja, és onnantól **minden könyvben jól
  mondja**. Ha épp szól a felolvasás, a mondatot rögtön újra is mondja a
  javítással. A szabályok a beállításokban áttekinthetők és törölhetők.
  - A csere a **szó elejéhez kötött, de a végződést nem bántja**: a `Bree`
    szabály a „Breeben" és a „Breevel" alakot is eltalálja, mert a magyarban
    a rag a szó végén van.
  - A csere **csak azon a szövegen történik, amit a motornak átadunk** — a
    könyv szövegéhez nem nyúlunk, így a kiemelt mondat nem csúszik el, és a
    keresés is az eredetiben keres tovább.
- **Wikipédia**: a menüből választott szót átadja a böngészőnek. Az appnak
  **továbbra sincs internet-engedélye** — nem tölt le semmit, csak megkéri a
  rendszert, hogy nyissa meg a címet. A szócikk nyelve a felület nyelvét
  követi.
- **Idézetkártya**: a mondatból megosztható kép, a futó színsémával.
- **Bionic Reading**: minden szó első ~40%-a félkövér, kapcsolható. A
  kapcsoló az olvasó hangolósávjában van, a betűméret mellett.
- **A hosszú nyomás kapcsolható** (Beállítások → Olvasás és vezérlés):
  alapból a **menüt** nyitja; átkapcsolva **azonnal könyvjelzőt tesz**, a
  menü pedig egyszeri koppintásra jön elő. Mindkettő elérhető marad, csak
  cserélődik a két gesztus.
- Szövegkijelölés nincs, és ez szándékos: harcolna a dupla koppintással,
  ami a felolvasást indítja. Ahol szó kell, ott a menü a mondat szavait
  kínálja fel koppintható jelvényként.
- 24 új egységteszt (kiejtés, mondathatárok, szóválasztás, bionic szedés);
  összesen 79 teszt fut.

### [4.2.0] — 2026-09-01

**Pöccintés a nézetek között**

- A **könyvtár** és a **fájlböngésző** egyetlen lapozható felület lett:
  jobbra-balra pöccintve váltasz köztük. A cím mellett két pötty mutatja,
  hol állsz. A rendszer-vissza a második lapról az elsőre visz, nem lép ki.
- A **polc kimarad a lapozásból**: ott a pöccintés már a könyvek közti
  mozgást jelenti, a két gesztus ütné egymást.
- A **helyi menü mindkét nézetben ugyanaz**, és ugyanúgy, hosszú nyomásra
  jön elő. Az első pontja az **adatlap**, utána a jegyzet és a
  fájlműveletek. Korábban a listában a hosszú nyomás egyből az adatlapot
  nyitotta, a böngészőben pedig a menüt.

### [4.1.0] — 2026-09-01

**Fájlműveletek és saját jegyzetek**

- **Átnevezés, áthelyezés, másolás, törlés** minden nézetből. A lényeg nem a
  fájlmozgatás, hanem hogy **minden hozzá kötött adat is követi a fájlt**:
  a katalógusbejegyzés, az olvasási haladás, a könyvjelzők, a jegyzet és a
  bélyegkép. Fájlkezelőben elvégezve mindez csendben elveszne.
- A **törlés mindig rákérdez**, és a fájl nevét is megmutatja.
- Az **olvasóban nincs fájlművelet**: az épp olvasott könyvet nem nevezzük át
  és nem töröljük magad alól. Jegyzetet ott is írhatsz hozzá.
- **Saját jegyzetek**: bármit hozzáfűzhetsz egy könyvhöz. A listában jel
  mutatja, melyikhez van, és a **kereső a jegyzetekben is keres**.
- 7 új egységteszt a fájlnév-ellenőrzésre (összesen 55).

### [4.0.0] — 2026-08-31

**Valódi borítók**

- **Borítókinyerés** magukból a könyvfájlokból: **EPUB** (az OPF háromféle
  jelölése), **MOBI/AZW3** (EXTH-rekord), **FB2** (base64), **PDF** (az első
  oldal kirajzolása). Ahol nincs borító, marad a címből és a szerzőből
  rajzolt.
- **Bélyegkép-tár**: WebP, 320×480, kb. 20 KB/db, mérete látszik a
  beállításokban és törölhető.
- **Két menetben**: a metaadatok gyorsan végigfutnak, a borítók utána, a
  háttérben töltődnek. Amit egyszer kinyertünk, azt nem próbáljuk újra.
- **Borítók a listában** kapcsolóval, alapból kikapcsolva.
- **„Most szól" sáv** minden képernyő alján: a könyv címe és a haladás, egy
  koppintással vissza a könyvhöz, gombbal bárhonnan elnémítható.
- 16 új egységteszt, köztük a MOBI bájtpontos offset-számolása.

### [3.2.0] — 2026-08-30

- **Indítás/szünet gomb minden képernyőn.** Felolvasás közben más nézetre
  váltva eddig nem lehetett elnémítani a könyvet.
- A teljes leállítás (értesítés → Stop) mostantól üríti a lejátszó állapotát;
  eddig a leállított könyv „betöltve" maradt.

### [3.1.1] — 2026-08-30

**Nagytakarítás** — nem új funkció, a kód rendberakása.

- **32 egységteszt**, eddig egy sem volt. A parsereket és a szövegkezelést
  fedik — ott a legveszélyesebb a hiba, mert nem omlik össze semmi, csak
  rosszul lesz felolvasva egy könyv.
- **Egy adatlap három helyett**; az olvasó 840 soros függvénye öt fájl lett;
  a ViewModel kettévágva (katalógus / fájlböngésző); a beállítások kilenc
  kártyája külön composable.
- **Három valódi hiba** derült ki és javult: a fájlböngésző üresen nyílt
  hidegindítás után; a böngészés aktuális mappája számított a könyvtár
  gyökerének is; a `&Otilde;` / `&odblac;` entitások nem oldódtak fel.

### [3.1.0] — 2026-08-30

**A lista lett a nyitóképernyő**

- A polc háromezer könyvnél használhatatlannak bizonyult: lapozgatva
  képtelenség megtalálni egy könyvet.
- **Kereső**, ami egyszerre nézi a címet, a szerzőt és a fájlnevet,
  ékezetre érzéketlenül. A szűrés a memóriában fut.
- **Betűsáv**, ami csak azokat a kezdőbetűket mutatja, amikhez van könyv.
- **Formátumjelvény minden soron**; ami nem olvasható fel, az szürke.
  **Formátum-szűrő** darabszámmal.
- Koppintások: egy = kijelölés, kettő = megnyitás és felolvasás, hosszú =
  adatlap, tetején a borítóval.
- A **polc megmaradt**, és a lista szűrt eredményét mutatja.

### [3.0.0] — 2026-08-30

**Az app mostantól mindenkinek szól**

- **A külső katalógus megszűnt.** Korábban be lehetett tölteni egy PC-n
  készített adatbázist; ez egyetlen gyűjteményhez volt szabva. Mostantól az app
  **maga építi a katalógust** a telefonon lévő könyvekből — bárki telepíti,
  nála ugyanúgy működik.
- **A szkennelés és a katalógusépítés összevonva**: egyetlen *Könyvtár
  beolvasása* művelet, ami metaadatot nyer ki és katalógusba ír. Változatlanul
  inkrementális: a meglévő bejegyzésekhez nem nyúl.
- **A katalógus látható fájl marad** (`Download/KonyvtarTTS/sajat_katalogus.db`),
  így túléli az app újratelepítését, és PC-n is megnyitható.

**Polc mint nyitóképernyő**

- Lapozható **borítónézet**: a könyveket úgy nézegetheted, mint a polc előtt
  állva. A borító alatt **haladás-csík** mutatja, hol tartasz; ha el sem
  kezdted a könyvet, nincs csík.
- Ahol nincs kinyert borítókép, **tipográfiai borítót** rajzolunk a címből és a
  szerzőből, a címből származtatott állandó színnel.
- **Olvasási számlálók** a polc tetején (*Elolvasva* / *Folyamatban*),
  koppintásra megnyílik a megfelelő lista.

**Indulási varázsló**

- Van mappa és katalógus → egyből a polc. Van mappa, de nincs katalógus →
  felajánlja a beolvasást. Nincs semmi → előbb mappát kér, aztán beolvasást.
  Üres képernyő, amiről nem tudni, mit kezdjünk vele, nincs többé.

**Egyéb**

- **Haladás-csík az olvasó könyvadat-ablakában** (korábban lemaradt).
- **Hiányzó fájlok eltávolítása a katalógusból** — kézzel indítható gomb a
  beállításokban; magától soha nem töröl semmit.
- Új dokumentum: **[fejlesztési terv](docs/TERV.md)**, benne az elkészült és a
  tervezett fázisok, döntésekkel és indoklással.

### [2.0.0] — 2026-08-30

**Tíz nyelven beszél az app**

- A teljes kezelőfelület lefordítva: **magyar, angol, német, francia, spanyol,
  portugál, lengyel, cseh, szlovák és orosz**. Mind a 237 szöveg minden
  nyelven megvan — a gomboktól a hibaüzenetekig és a CSV-export
  oszlopfejléceiig.
- **A felület nyelve és a felolvasás nyelve külön állítható.** Olvashatsz
  magyar felülettel angol könyvet, vagy fordítva.
- Új beállítás: *A felület nyelve* — rendszerkövetés vagy kézi választás,
  minden nyelv a saját nevén. A váltás azonnal érvényes.
- Ismeretlen nyelvű telefonon az **angol** az alapértelmezés, hogy bárhol a
  világon érthető legyen.
- Android 13-tól az app a rendszer nyelvi beállításai közt is megjelenik.
- A fejezetfelismerés is nemzetközi lett: a magyar mellett angol, német,
  francia, spanyol, lengyel, cseh és orosz fejezetszavakat is felismer.

**Új nyelv hozzáadása**: másold a `res/values/strings.xml` fájlt egy új
`res/values-<kód>/` mappába, fordítsd le az értékeket, és vedd fel a nyelvet
a `data/AppLanguages.kt` listájába. Kódolás nem kell hozzá.

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

### [4.6.0] — 2026-09-04

**The character list, measured on two real novels**

We tried to read out **who is who** as well, from appositions and kinship
phrasing in the text.

**It failed, and so it is not in here.** Measured on two real novels — a
contemporary translation and a Thackeray — it produced an introduction for
one character out of twenty-five, and that one was wrong. What it failed on
is instructive:

- A comma does not only introduce an apposition. It also introduces direct
  address ("Pantaleon, fetch the cognac") and adverbial clauses.
- With kinship phrasing there are two names in the sentence, and without real
  parsing there is no telling which one it is about: the list ended up with
  both "Naum → Bernsztajn's son" and "Bernsztajn → Naum's son" — the
  father-son relation **reversed as well**.
- The car brand "Fiat" looked like a kinship word, because the Hungarian stem
  `fia` (son) plus the suffix `t` matches it.

A wrong introduction is worse than none: the latter stays silent, the former
misleads. This task needs real language understanding.

**What did go in, because measurement says it works:**

- **Who they appear with.** Each character now shows their three most
  frequent companions. This gave the most useful signal by far: in the test
  book you could see at once who belongs with whom.
- **The displayed name is the most frequent form, not the shortest.** Without
  this, the 457 mentions of "Newcome" showed up **as "New"**, because "New"
  also occurs on its own ("New Street") and being shorter it became the stem.
  Likewise "Eth" for Ethel and "Hob" for Hobson.
- **Long-vowel suffixes now fold too.** "Szapiro" and "Szapirót" appeared as
  separate characters with 493 and 44 mentions — the same man, cut in two.

Name detection and speed proved good: 150 ms on a 615,000-character novel,
and the top of the list really was the main cast.

What still does not work: **place names** are not told apart from people
(Warsaw, London, Palestine are on the list), nor are **titles** (Sir, Lady,
Lord).

### [4.5.0] — 2026-09-04

**Knowing the book: "Where was I?" and a character index**

Both live in the reader's **⋮ menu**, and **neither ever looks past your
reading position**. That is not fussiness: the rest of the chapter is a
spoiler, and a spoiler cannot be undone.

- **"Where was I?"** — the **four most characteristic sentences** of the part
  you last listened to, in their original order. Not a summary and not a
  retelling: the book's own sentences. Someone reading with their eyes flips
  back a page; someone listening cannot — this replaces that.
- **Characters** — who has appeared so far, ranked by frequency, each with the
  sentence of its first appearance. Tap a name to jump to that paragraph.

Both run **locally, with no AI and no internet** — the app still has no
internet permission.

Under the bonnet:

- Sentences are scored with **TF-IDF**, where the corpus is the text you have
  read so far. Anything present in every paragraph gets zero weight, so
  articles and conjunctions fall out by themselves. **No stopword list to
  maintain across ten languages**, and it works on a book in any language.
- Name detection is dictionary-free too: what is capitalised and does **not
  only** appear at the start of a sentence is a name.
- Inflected forms fold onto their stem: "Gandalfot", "Gandalfnak" and
  "Gandalffal" all land under "Gandalf". Hungarian **stem-final vowel
  lengthening** is handled as well (`Anna → Annát`); without it every name
  ending in `-a` or `-e` would have been missed.
- 18 new unit tests, including ones written specifically to keep either
  feature from ever looking past the reading position. 97 in total.

It is not perfect: reliable for the main characters, and it may miss the odd
inflected form of a rarer name.

### [4.4.0] — 2026-09-02

**A permanent navigation bar in every view**

- **A bottom bar everywhere**, with three buttons: **Library · Files ·
  Reading**. One tap from any view to any other. The active view is
  highlighted, so you can see where you are — not just where you can go.
- **The Reading button works even when nothing is playing**: it opens the
  book you listened to last. It only greys out before you have opened any
  book at all.
- **On arriving at the reader, the current sentence glows brighter for a
  few seconds**, then settles back to the usual highlight. You can jump
  here from anywhere now, and the first question is always the same —
  "where were we?" — which this answers at a glance.
- The **now-playing strip** is no longer a separate bar: it sits directly
  above the navigation, as one unit. Two stacked bars would have taken
  space away from the text, precisely in the reader.
- Swiping between the library and the file browser **still works**; the
  bar's buttons drive the same pager, so the two do not fight.
- The **folder picker leaves the bar out**, deliberately: that is a task to
  finish, not a view.

### [4.3.1] — 2026-09-02

**Headset buttons: four bugs fixed**

Night-shift testing revealed that the Bluetooth headset's buttons only worked
while the app was on screen and every other player had been killed. Four
separate bugs were compounding; all fixed:

- **We gave up audio focus while paused.** The system routes headset buttons
  to the app holding focus, and skips whoever lets go — which is why the next
  button press started a different player. Focus now stays with us while
  paused, and is only released on a full stop.
- **We left the foreground while paused.** That let the system drop the
  service at any time, and with it the MediaSession the buttons needed to
  reach. Hence "it only works while it's on screen". We now stay in the
  foreground while a book is loaded; stopping is done from the notification.
- **We treated transient focus loss as permanent.** A single notification
  sound stopped narration for good. The two are now told apart: after a
  transient loss it **resumes by itself** once it gets the audio back.
  Ducking is not viable for speech, so we still pause — but we come back.
- **The `MediaButtonReceiver` was missing.** If the service did stop, a button
  press had nowhere to land. The system can now wake the app with it.

### [4.3.0] — 2026-09-01

**An action menu on the text, and a pronunciation dictionary**

- **An action menu on the book's text**: *Bookmark · Pronunciation ·
  Wikipedia · Quote card · Copy*. The menu works on the **sentence you
  touched**, and shows it — the same chunk the narrator speaks as one unit.
- **A pronunciation dictionary.** When the voice mangles a name: long press
  → *Pronunciation* → type how it should sound, and **every book says it
  properly from then on**. If narration is running, the sentence is
  re-spoken straight away with the fix. The rules can be reviewed and
  deleted in the settings.
  - The substitution is **anchored to the start of a word but leaves the
    ending alone**: a `Bree` rule also catches "Breeben" and "Breevel",
    because in Hungarian the suffix sits at the end.
  - It only touches **the text handed to the engine** — never the book's
    text — so the highlighted sentence does not shift and search keeps
    searching the original.
- **Wikipedia**: hands the word picked in the menu to the browser. The app
  still **has no internet permission** — it downloads nothing, it only asks
  the system to open an address. The article language follows the interface
  language.
- **Quote card**: a shareable image made from the sentence, in the running
  colour scheme.
- **Bionic Reading**: the first ~40% of every word in bold, toggleable. The
  switch is in the reader's tuning row, next to the font size.
- **The long press is configurable** (Settings → Reading and controls): by
  default it opens the **menu**; switched over it **bookmarks right away**
  and the menu opens on a single tap. Both stay available, the two gestures
  just swap.
- There is no text selection, deliberately: it would fight the double tap
  that starts narration. Where a word is needed, the menu offers the
  sentence's words as tappable chips.
- 24 new unit tests (pronunciation, sentence bounds, word picking, bionic
  weighting); 79 tests in total.

### [4.2.0] — 2026-09-01

**Swiping between the views**

- The **library** and the **file browser** became one swipeable surface: swipe
  left or right to switch. Two dots next to the title show where you are.
  System back goes from the second page to the first instead of leaving.
- **The shelf stays out of the swipe**: there a swipe already means moving
  between books, and the two gestures would fight each other.
- **The context menu is the same in both views** and opens the same way, on
  long press. Its first entry is the **details sheet**, followed by the note
  and the file operations.

### [4.1.0] — 2026-09-01

**File operations and personal notes**

- **Rename, move, copy, delete** from every view. The point is not moving
  files but that **everything attached to the file follows it**: the catalogue
  entry, the reading progress, the bookmarks, the note and the thumbnail.
  Doing the same in a file manager would silently lose all of it.
- **Deleting always asks first** and shows the file name.
- **No file operations in the reader**: the book you are reading will not be
  renamed or deleted from under you. You can still write a note there.
- **Personal notes**: attach anything to a book. A mark in the list shows which
  books have one, and **search looks inside the notes too**.
- 7 new unit tests for file-name validation (55 in total).

### [4.0.0] — 2026-08-31

**Real covers**

- **Cover extraction** from the book files themselves: **EPUB** (three OPF
  conventions), **MOBI/AZW3** (EXTH record), **FB2** (base64), **PDF** (first
  page rendered). Where there is no cover, the drawn one stays.
- **Thumbnail store**: WebP, 320×480, about 20 KB each; its size is shown in
  settings and it can be cleared.
- **Two passes**: metadata runs through quickly, covers load afterwards in the
  background. What was extracted once is never attempted again.
- **Covers in the list** behind a toggle, off by default.
- **A now-playing bar** at the bottom of every screen: the book's title and
  progress, one tap back to it, and a button to silence it from anywhere.
- 16 new unit tests, including the MOBI byte-exact offset arithmetic.

### [3.2.0] — 2026-08-30

- **A play/pause button on every screen.** Switching views while a book was
  being read aloud left no way to silence it.
- A full stop (notification → Stop) now clears the player state; a stopped
  book used to stay "loaded".

### [3.1.1] — 2026-08-30

**Cleanup** — not a feature, putting the code in order.

- **32 unit tests**; there were none. They cover the parsers and the text
  handling — where a bug is most dangerous, because nothing crashes, a book is
  just read out wrong.
- **One details sheet instead of three**; the reader's 840-line function became
  five files; the view model split in two (catalogue / file browser); the nine
  settings cards became separate composables.
- **Three real bugs** surfaced and were fixed: the file browser opened empty
  after a cold start; the browser's current folder also counted as the library
  root; the `&Otilde;` / `&odblac;` entities were not decoded.

### [3.1.0] — 2026-08-30

**The list became the start screen**

- The shelf turned out to be unusable with three thousand books: flipping
  through covers, you cannot find anything.
- **Search** across the title, the author and the file name at once,
  accent-insensitive. Filtering runs in memory.
- **A letter bar** showing only the initials that actually have books.
- **A format badge on every row**; formats that cannot be read aloud are grey.
  **A format filter** with counts.
- Taps: one = select, two = open and read aloud, long = details sheet, with the
  cover on top.
- **The shelf is still there**, showing whatever the list currently filters.

### [3.0.0] — 2026-08-30

**The app is now for everyone**

- **The external catalogue is gone.** It used to be possible to load a database
  built on a PC, which was tailored to one collection. From now on the app
  **builds the catalogue itself** from the books on the phone — it works the
  same for anyone who installs it.
- **Scanning and catalogue building merged** into a single *Read the library*
  operation that extracts metadata and writes the catalogue. Still incremental:
  existing entries are left untouched.
- **The catalogue stays a visible file**
  (`Download/KonyvtarTTS/sajat_katalogus.db`), so it survives reinstalling the
  app and can be opened on a PC.

**The shelf is the start screen**

- A pageable **cover view**: browse your books like standing in front of a
  shelf. Under each cover a **progress bar** shows where you are; no bar means
  you have not started the book.
- Where no cover image is available a **typographic cover** is drawn from the
  title and author, in a colour derived from the title so it stays constant.
- **Reading counters** on top of the shelf (*Finished* / *In progress*);
  tapping one opens the matching list.

**Startup wizard**

- Folder and catalogue present → straight to the shelf. Folder but no catalogue
  → it offers the scan. Nothing set up → it asks for the folder first, then the
  scan. No more empty screen you cannot do anything with.

**Other**

- **Progress bar in the reader's book info dialog** (it was missing).
- **Remove missing files from the catalogue** — a manual button in settings; it
  never deletes anything on its own.
- New document: the **[roadmap](docs/ROADMAP.en.md)**, listing finished and
  planned phases with the reasoning behind each decision.

### [2.0.0] — 2026-08-30

**The app now speaks ten languages**

- The entire interface is translated: **Hungarian, English, German, French,
  Spanish, Portuguese, Polish, Czech, Slovak and Russian**. All 237 strings
  exist in every language — from buttons to error messages and the CSV export
  column headers.
- **Interface language and narration language are set separately.** Read an
  English book with a Hungarian interface, or the other way round.
- New setting: *Interface language* — follow the system or pick manually, each
  language shown in its own name. The switch takes effect immediately.
- On a phone with an unsupported system language the app falls back to
  **English**, so it stays understandable anywhere.
- From Android 13 the app also appears in the system language settings.
- Chapter detection went international too: alongside Hungarian it now
  recognises English, German, French, Spanish, Polish, Czech and Russian
  chapter words.

**Adding a language**: copy `res/values/strings.xml` into a new
`res/values-<code>/` folder, translate the values, and add the language to the
list in `data/AppLanguages.kt`. No coding required.

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
