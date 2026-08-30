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

## 2. fázis — Borítók és a polc kiteljesítése

- **Borítókinyerés**: EPUB (OPF `cover`), MOBI/AZW3 (EXTH 201), FB2
  (`<binary>` base64), PDF (első oldal kirajzolása). Ahol nincs borító, marad
  a mostani **tipográfiai borító**.
- **Bélyegkép-tár**: kicsinyítve (kb. 320×480 WebP, ~20 KB/db), méret látszik
  a beállításokban és törölhető.
- **Két menetben**: előbb a metaadatok futnak végig gyorsan (a polc máris
  használható), utána a háttérben töltődnek a valódi borítók.
- **A fájllistában is** lehessen borító — **kapcsolóval**, alapból kikapcsolva,
  mert a magasabb sorokba kevesebb könyv fér.

## 3. fázis — Olvasási élmény

- **Bionic Reading**: minden szó első ~40%-a félkövér, kapcsolható.
- **Hosszú nyomás → műveletmenü** a mostani azonnali könyvjelző helyett:
  *Könyvjelző · Wikipédia · Idézetkártya · Másolás*.
- **Wikipédia**: a kijelölt szót átadja a böngészőnek — így az appnak
  **továbbra sem kell internet-engedély**.
- **Idézetkártya**: a kijelölt szövegből kép, az aktuális színsémával,
  megosztható.

## 4. fázis — Tudás a könyvről

- **Karakternévtár**: az addig olvasott részből kigyűjti a nagybetűs neveket,
  amelyek nem csak mondat elején állnak (a meglévő mondathatár-felismerőre
  épül). Gyakoriság szerint rendez, mindegyikhez az első előfordulás mondata.
  Spoilermentes, mert csak az olvasott részt nézi. A magyar ragozást tőre
  vonással kezeljük — ez nem tökéletes, de a fő szereplőknél működik.
- **„Hol voltam?"**: a legutóbbi fejezet mondatait pontozzuk a fejezet
  leggyakoribb tartalmi szavai alapján, és a 3–4 legjellemzőbbet mutatjuk
  eredeti sorrendben. Nem a történetet meséli el, hanem **visszahelyez a
  szövegbe** — és ehhez nem kell mesterséges intelligencia.

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

- **Kiejtési szótár**: könyvenként vagy globálisan megadható átírások a TTS-nek
  (`Bree → Brí`), mert a magyar hang a kitalált neveket rendre elrontja.
  Teljesen helyi, apró munka, hosszú hallgatásnál nagy különbség.
- **Sorköz és margó** állítása az olvasóban, a betűméret mellé.
- **Lapozós mód** a mostani folyamatos görgetés alternatívájaként.
