# Vox Libris — fejlesztési terv

🇭🇺 Magyar (ez a lap) · 🇬🇧 [English](ROADMAP.en.md) · ⬅ [Vissza a főoldalra](../README.md)

Ez a lap rögzíti, mit építünk, milyen sorrendben, és **miért úgy**. A már
elkészült részeket is benne hagyjuk, hogy később visszakereshető legyen a
döntések indoklása.

---

## Az alapelv, ami mindent eldönt

**Az appnak nincs internet-engedélye.** Ez nem véletlen: így technikailag
képtelen adatot küldeni bárhová, és ezt a README is állítja. Minden tervezett
funkciónál az első kérdés, hogy megvalósítható-e helyben. Ami nem, azt vagy
kerülő úton oldjuk meg, vagy tudatosan elhalasztjuk.

A második alapelv: **az app a telefonon lévő könyvekből maga építi a
katalógusát.** Nincs külső adatbázis, nincs szerver, nincs fiók — bárki
telepíti, nála ugyanúgy működik.

---

## 1. fázis — Nagytakarítás és alapok ✅ *(kész)*

- **A külső katalógus kivezetése.** Korábban be lehetett tölteni egy PC-n
  készített `.db` fájlt. Ez egyetlen ember gyűjteményéhez volt szabva, ezért
  megszűnt: a katalógust az app építi.
- **A szkennelés és a katalógusépítés összevonása.** Korábban két, félig
  átfedő művelet volt; most egy: *Könyvtár beolvasása*.
- **A katalógus látható fájl marad** (`Download/KonyvtarTTS/sajat_katalogus.db`),
  így túléli az app újratelepítését, és PC-n is megnyitható.
- **A polc lett a nyitóképernyő**, a következő indulási logikával:
  - van mappa és van katalógus → egyből a polc,
  - van mappa, de nincs katalógus → felajánlja a beolvasást,
  - nincs semmi → előbb mappát kér, aztán ajánlja a beolvasást.
- **Olvasási számlálók** a polc tetején (elolvasva / folyamatban), koppintásra
  megnyílik a lista.
- **Haladás-csík** az olvasó könyvadat-ablakában (korábban lemaradt).

## 1. fázis, utólagos javítás — a lista lett a nyitóképernyő ✅ *(kész)*

A polc a teszten megbukott: **3500 könyvnél a lapozgatás reménytelen**. A
nyitóképernyő ezért a **lista** lett, és minden a megtalálást szolgálja:

- **Kereső**, ami egyszerre nézi a **címet, a szerzőt és a fájlnevet**, ékezetre
  érzéketlenül (a „jozsef” megtalálja a Józsefet). A szűrés a memóriában fut,
  ezért gépelés közben azonnal frissül.
- **Betűsáv** a kereső alatt: egy koppintás a kezdőbetűre, és csak azok a
  könyvek maradnak. A sáv **csak azokat a betűket mutatja, amikhez tényleg van
  könyv** — nincs üresbe vezető gomb. Rendezéstől függően a cím vagy a szerző
  kezdőbetűjét nézi, az ékezetes betűk az alapbetűhöz sorolódnak.
- **Formátumjelvény minden soron** (EPUB, PDF, MOBI…), színnel megkülönböztetve.
  Amiből nem tudunk szöveget kinyerni, az **szürke** — így a listában látszik,
  melyik könyv fog megszólalni.
- **Formátum-szűrő** a jelvények mellé: darabszámmal együtt sorolja fel, mi van
  a telefonon (pl. EPUB 2100, PDF 900), és egy koppintással szűkít.
- **Koppintások**: egy = kijelölés, **kettő = megnyitás és felolvasás**,
  **hosszú nyomás = adatlap**. Az adatlap tetején a borító.
- **Az adatlap megmondja, mire számíthatsz** az adott formátumtól: hogy a
  fejezetek pontosak-e, vagy hogy a PDF-nél a tördelés beleszólhat.

A **polc megmaradt**, egy koppintásra a felső sávban — és ugyanazt mutatja,
amit a lista éppen: ha rákerestél valamire vagy leszűkítetted egy betűre, a
polcon is csak azok a könyvek lapozhatók.

## Nagytakarítás ✅ *(kész)*

Nem új funkció, hanem a kód rendberakása — azért itt, mert a következő
fázisok pont a legzűrösebb részekbe érkeznek.

- **Biztonsági háló: 32 egységteszt.** Eddig egy sem volt. A tesztek a
  parsereket és a szövegkezelést fedik — ott a legveszélyesebb a hiba, mert
  nem omlik össze semmi, csak rosszul lesz felolvasva egy könyv. Ehhez a
  parserek `android.util.Xml` helyett szabványos XML-olvasót használnak, így
  emulátor nélkül futnak. Futtatás: `gradlew testDebugUnitTest`.
- **Egy adatlap három helyett.** A könyv adatlapja három, betű szerint azonos
  másolatban élt; emiatt landolt egy korábbi javítás csak kettőben.
- **Az olvasó szétszedve.** A 840 soros composable öt fájl lett: állapot,
  felső sáv, vezérlősáv, szöveg, könyvjelzők.
- **A ViewModel kettévágva**: külön a katalógus, külön a fájlböngésző.
- **A beállítások kilenc kártyája** külön composable, saját állapottal.

Közben három valódi hiba derült ki, mind javítva: a fájlböngésző üresen
nyílt hidegindítás után; a böngészés aktuális mappája számított a könyvtár
gyökerének is; és a `&Otilde;` / `&odblac;` entitások nem oldódtak fel.

## 2. fázis — Borítók és a „most szól" sáv ✅ *(kész)*

- **Borítókinyerés** magukból a könyvfájlokból. Minden formátumnak megvan a
  maga rejtekhelye: az **EPUB** az OPF-ben jelöli meg (háromféleképpen is:
  `<meta name="cover">`, `properties="cover-image"`, vagy egy „cover" nevű
  kép), a **MOBI/AZW3** egy EXTH-rekordban tartja a kép rekordszámát, az
  **FB2** base64-ben ágyazza be, a **PDF**-nél az első oldalt rajzoljuk ki.
  Ahol nincs borító, marad a címből és a szerzőből rajzolt.
- **Bélyegkép-tár**: kicsinyítve, WebP-ben (320×480, ~20 KB/db), az app saját
  mappájában. Mérete látszik a beállításokban és törölhető — nem érték, bármikor
  újra kinyerhető.
- **Két menetben**: a metaadatok gyorsan végigfutnak és a könyvtár máris
  használható, a borítók utána, a háttérben töltődnek. Amit egyszer
  kinyertünk, azt nem próbáljuk újra.
- **Borítók a listában** kapcsolóval, alapból kikapcsolva.
- **„Most szól" sáv** minden képernyő alján: látod, melyik könyv szól és hol
  tart, egy koppintással visszaugrasz hozzá, a gombbal bárhonnan
  elnémítható. Csak akkor látszik, ha van betöltött könyv.

A borítókinyerésre **16 új egységteszt** ügyel (a MOBI bájtpontos
offset-számolására is), így összesen 48 teszt fut.

## Fájlműveletek és saját jegyzetek ✅ *(kész)*

**Átnevezés, áthelyezés, másolás, törlés** — minden nézetből ugyanaz a menü.

Az érdemi rész nem a fájlmozgatás, azt egy fájlkezelő is tudja. Az érdemi
rész, hogy **minden hozzá kötött adat is követi a fájlt**: a
katalógusbejegyzés, az olvasási haladás, a könyvjelzők, a jegyzet és a
bélyegkép. Ugyanez fájlkezelőben elvégezve mindez csendben elveszne, és a
könyv újként jelenne meg, nulláról.

- A **törlés** mindig rákérdez, és a fájl nevét is megmutatja — ez az
  egyetlen visszafordíthatatlan művelet.
- Az **olvasóban nincs fájlművelet**: az épp olvasott könyvet nem nevezzük át
  és nem töröljük magad alól. Jegyzetet viszont ott is írhatsz hozzá.
- A másolat ugyanannak a műnek egy másik fájlja lesz a katalógusban — a séma
  eleve megengedi, hogy egy könyvhöz több fájl tartozzon.

**Saját jegyzetek**: bármit hozzáfűzhetsz egy könyvhöz. A listában kis jel
mutatja, melyikhez van jegyzet, és a **kereső a jegyzetekben is keres**.

## Pöccintés a nézetek között ✅ *(kész)*

A **könyvtár** és a **fájlböngésző** egyetlen lapozható felület lett: jobbra-balra
pöccintve váltasz köztük. A cím mellett két pötty mutatja, hol állsz — e nélkül a
pöccintés láthatatlan lenne.

A rendszer-vissza a második lapról az elsőre visz, nem lép ki az appból.

**A polc kimarad a lapozásból**, és ez szándékos: ott a pöccintés már a könyvek
közötti lapozást jelenti, a két gesztus ütné egymást.

**A helyi menü mindkét nézetben ugyanaz**: hosszú nyomásra jön elő, és az első
pontja az **adatlap**, utána a jegyzet és a fájlműveletek.

## 3. fázis — Olvasási élmény ✅ *(kész)*

- **Műveletmenü a szövegen**: *Könyvjelző · Kiejtés · Wikipédia ·
  Idézetkártya · Másolás*. A menü a **megérintett mondattal** dolgozik, és
  meg is mutatja, melyikkel — így nem kell találgatni, mire vonatkozik a
  művelet. A mondat azért jó egység, mert a felolvasó is ezzel dolgozik:
  amit a menü mutat, az pontosan az, amit hallasz.
- **Kiejtési szótár**, ami eredetileg csak ötlet volt a lap alján, és
  végül a fázis legfontosabb darabja lett. Hallod, hogy a hang elrontja a
  nevet → hosszú nyomás → *Kiejtés* → beírod, hogyan mondja → **onnantól
  minden könyvben jól mondja**. Ha épp szól a felolvasás, a mondatot
  rögtön újra is mondja a javítással.
- **Wikipédia**: a szót átadja a böngészőnek, tehát az appnak **továbbra
  sincs internet-engedélye** — nem tölt le semmit, csak megkéri a
  rendszert, hogy nyissa meg a címet. A szócikk nyelve a felület nyelvét
  követi.
- **Idézetkártya**: a mondatból kép a futó színsémával, megosztható. A
  kártya a gyorsítótárba kerül, onnan adja tovább a FileProvider.
- **Bionic Reading**: minden szó első ~40%-a félkövér, kapcsolható. Az
  olvasó hangolósávjában van a kapcsoló, a betűméret mellett — ott látszik
  rögtön, mit csinál a szöveggel.

### Amiben eltértünk a tervtől, és miért

- **Nincs szövegkijelölés.** A terv „a kijelölt szót" mondta, de a Compose
  szövegkijelölése harcolna a dupla koppintással (az indítja a
  felolvasást), nagy betűmérettel pedig egy kézzel amúgy is kínlódás.
  Helyette a menü a mondat **szavait kínálja fel jelvényként**: ráböksz a
  névre, és kész. Ez lett a kiejtési szótár felvitele is.
- **A hosszú nyomás kapcsolható.** A terv szerint a menü egyszerűen
  átvette volna a hosszú nyomást a könyvjelzőtől. Csakhogy a könyvjelzőzés
  a leggyakoribb művelet a szövegben, és egy koppintással drágább lett
  volna. Így most **beállítás**: alapból a hosszú nyomás nyitja a menüt;
  átkapcsolva a hosszú nyomás könyvjelzőz, a menü pedig egyszeri
  koppintásra jön. Mindkettő elérhető marad, csak cserélődik a két gesztus.
- **A kiejtési szótár globális**, nem könyvenkénti. A félremondott nevek
  többnyire sorozaton át és több fájlban is visszatérnek, így egyszer kell
  megadni őket. A csere **szókezdethez kötött, de a végződést nem bántja**
  (`Bree` → a „Breeben" is jó lesz), mert a magyarban a rag a szó végén
  van, a szótő pedig elöl.
- **A csere csak a felolvasandó szövegen történik**, a könyv szövegéhez nem
  nyúlunk. Ez nem szépészeti kérdés: így a képernyőn kiemelt mondat
  karakterpozíciói nem csúsznak el, és a keresés is az eredeti szövegben
  keres tovább.

A fázisra **24 új egységteszt** ügyel (kiejtés, mondathatárok,
szóválasztás, bionic szedés), így összesen 79 teszt fut.

## 4. fázis — Tudás a könyvről ✅ *(kész)*

Mindkét funkció az olvasó ⋮ menüjéből érhető el, és **egyik sem néz túl az
olvasási pozíciódon**. Ez nem óvatoskodás: a fejezet hátralévő része spoiler,
és a spoilert nem lehet visszacsinálni.

- **„Hol voltam?"** — a legutóbb hallgatott rész **négy legjellemzőbb
  mondata**, eredeti sorrendben. Nem összefoglaló és nem meséli el a
  történetet: a könyv saját mondatai. Aki a szemével olvas, visszalapoz egy
  oldalt; aki hallgat, nem tud — ezt pótolja.
- **Karakternévtár** — kik szerepelnek eddig, gyakoriság szerint,
  mindegyikhez az első előfordulás mondatával. Egy névre koppintva odaugrik
  ahhoz a bekezdéshez.

### Hogyan működik, szótár nélkül

A mondatok pontozása **klasszikus TF-IDF**, ahol a „korpusz" maga az eddig
olvasott szöveg. A képlet lényege, hogy ami *minden* bekezdésben ott van, az
pontosan nulla súlyt kap: `ln(n/n) = 0`. A névelő és a kötőszó így magától
esik ki — **nincs beépített kötőszólista, amit tíz nyelvre kellene
karbantartani**, és a dolog bármelyik nyelvű könyvön működik.

A névfelismerés hasonlóan szótár nélküli: ami nagybetűs, és **nem csak mondat
elején** áll, az név. A hétköznapi szavak is nagybetűsek mondatkezdéskor, de
mondat közepén már nem azok.

### Amiben eltértünk a tervtől, és miért

- **A felidézés nem fejezethez kötött, hanem pozícióhoz.** A terv „a
  legutóbbi fejezet" mondatairól szólt, de ennek két baja lett volna: ha a
  fejezet közepén hagytad abba, a fejezet hátralévő része **spoiler**; és
  PDF-nél vagy TXT-nél a fejezethatár bizonytalan. Így a szakasz egyszerűen
  a pozíciód előtti legfeljebb 40 bekezdés — mindig értelmes, minden
  formátumnál.
- **A küszöb két külön kérdésre bomlott.** Egy szereplőnél az, hogy *név-e*,
  és az, hogy *számít-e*, két külön dolog: az elsőt a mondat közepi nagybetű
  bizonyítja, a másodikat a darabszám. Ha a mondat közepi előfordulásból
  kérnénk kettőt, kimaradna az a szereplő, aki többnyire mondatot kezd.

### Amit a tesztírás derített ki

A magyar ragozás **megnyújtja a tővégi magánhangzót**: `Anna → Annát`,
`Kata → Katát`, `Emese → Emesét`. Az „Annát" tehát **nem** az „Anna" szóval
kezdődik, hanem az „Anná"-val — vagyis a sima előtag-egyezés minden `-a` és
`-e` végű nevet kihagyott volna az összevonásból, pedig magyar szövegben
abból van a legtöbb. A tő-egyeztetés ezért megengedi az `a→á` és `e→é`
váltást a tő utolsó betűjén.

A fázisra **18 új egységteszt** ügyel — köztük olyanok, amik kifejezetten azt
őrzik, hogy egyik funkció se nézzen a pozíción túlra. Összesen 97 teszt fut.

## 5. fázis — Statisztikák

- Olvasási ülések naplózása, ebből **olvasási sebesség (WPM)**, **a fejezet
  hátralévő ideje** (a hátralévő karakterekből és a beszédsebességből), és
  **hőtérkép** arról, mikor olvasol a legtöbbet.

---

## Amit tudatosan nem építünk (egyelőre)

| Ötlet | Miért nem most |
|---|---|
| AI-összefoglaló felhőben | API-kulcs, költség, és elveszne az „nincs internet" garancia. A kivonatos összefoglaló (4. fázis) a helyi megoldás. |
| Közösségi margójegyzetek | Szerver, fiókok, moderálás — ez már második termék. |
| Néma könyvklub | Ugyanaz: valós idejű szerver, moderálás. |
| Könyvkölcsönzés | Szerver + kényes szerzői jogi kérdések. |
| Sorozatok, jelvények | Olcsó megcsinálni, de nem ez hiányzik leginkább; ha bekerül, kapcsolhatóan. |

## Ötletek, amik menet közben jöttek

- ~~**Kiejtési szótár**~~ — megépült a 3. fázisban, globálisan.
- **Sorköz és margó** állítása az olvasóban, a betűméret mellé.
- **Lapozós mód** a mostani folyamatos görgetés alternatívájaként.
