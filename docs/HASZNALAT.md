# Vox Libris — használati útmutató

🇭🇺 Magyar (ez a lap) · 🇬🇧 [English](USAGE.en.md) · ⬅ [Vissza a főoldalra](../README.md)

> A **telepítés**, a szövegfelolvasó motor és a magyar hang beállítása, a
> Bluetooth-tudnivalók és a fordításhoz szükséges függőségek külön lapon
> vannak: **[Telepítés és függőségek](TELEPITES.md)**

---

## Tartalom

1. [Mire lesz szükséged](#1-mire-lesz-szükséged)
2. [Telepítés](#2-telepítés)
3. [Első indítás — engedélyek](#3-első-indítás--engedélyek)
4. [A katalógus beállítása](#4-a-katalógus-beállítása)
5. [Fájlböngésző](#5-fájlböngésző)
6. [Könyv részletei](#6-könyv-részletei)
7. [Olvasó képernyő](#7-olvasó-képernyő)
8. [Fülhallgató-gombok](#8-fülhallgató-gombok)
9. [Olvasási lista és exportálás](#9-olvasási-lista-és-exportálás)
10. [Beállítások](#10-beállítások)
11. [Hibaelhárítás](#11-hibaelhárítás)

---

## 1. Mire lesz szükséged

- **Android 11 vagy újabb** telefon.
- **Rendszer TTS motor** magyar hanggal. A legtöbb telefonon a Google
  Szövegfelolvasó már fent van; a magyar hangot egyszer le kell tölteni
  (Beállítások → Kisegítő lehetőségek → Szövegfelolvasó).
- **Könyvfájlok** a telefonon vagy SD-kártyán.
- Opcionálisan egy **SQLite katalógus** (`.db`) a metaadatokkal. Enélkül is
  működik az app, csak akkor nem lesz cím/szerző/fülszöveg a fájlok mellett.

## 2. Telepítés

1. Másold a telefonra az `app-release.apk` fájlt.
2. Koppints rá a fájlkezelőben, és telepítsd. Ha a rendszer tiltja, engedélyezd
   az adott alkalmazásnak az „ismeretlen források” telepítését.
3. Frissítéskor ugyanígy telepítsd rá az újat — **az adataid (pozíciók,
   könyvjelzők, szkennelés) megmaradnak.**

## 3. Első indítás — engedélyek

Az első indításnál egy magyarázó képernyő fogad:

- **„Minden fájl kezelése” engedély** — ez kell ahhoz, hogy az app lássa a
  könyveidet és a katalógust bárhol a tárolón. A gomb a rendszerbeállításokba
  visz; kapcsold be, majd lépj vissza az appba (magától észreveszi).
- **Értesítési engedély** (Android 13-tól) — ezt egy felugró ablak kéri. Ha
  megadod, a felolvasás vezérlői megjelennek az értesítési sávban is,
  a lezárt képernyőn szintén.

## 4. A katalógus beállítása

Az app automatikusan megkeresi az `ncore_konyvtar.db` fájlt a belső tároló és
az SD-kártya gyökerében, valamint a `Download`, `Documents` és `Books`
mappákban.

Ha máshol van, vagy más a neve: **Beállítások** (fogaskerék a jobb felső
sarokban) → **„Adatbázisfájl kiválasztása…”**, és tallózd ki.

A fejlécben mindig látod az állapotot: `68319 könyv` (zölden), vagy
`nincs DB` (pirosan).

> **Tipp:** a `.db` fájlt a **belső tárolóra** tedd, ne SD-kártyára. Az SQLite
> sok apró, véletlenszerű olvasást végez, ami a kártyán érezhetően lassabb.
> A könyvfájlok viszont nyugodtan lehetnek kártyán — azokat az app egyszer
> olvassa végig, utána a kinyert szöveg gyorsítótárból jön.
>
> Ha a PC-n frissíted a katalógust, ugyanoda, ugyanazzal a névvel másold
> felül — minden magától működik tovább, a pozícióid megmaradnak.

## 5. Fájlböngésző

Ez az app nyitóképernyője: sűrű, ikonmentes lista, hogy egy képernyőre minél
több sor férjen.

### Két nézet

- **Mappák** — a tároló valódi könyvtárszerkezete, ahogy megszoktad.
- **Katalógus** — minden korábban beszkennelt fájl **egyetlen listában**,
  mappáktól függetlenül. Nagy gyűjteménynél ez a hasznosabb: kereshetsz és
  rendezhetsz az egész könyvtáradban.

### Felső sáv

| Elem | Mit csinál |
|---|---|
| 📡 **radar** | rekurzív szkennelés az aktuális mappától lefelé + párosítás a katalógussal |
| 📊 **oszlopdiagram** | olvasási lista és statisztika |
| ⚙ **fogaskerék** | beállítások |
| **keresőmező** | szűrés fájlnév, cím és szerző szerint |
| 💾 **SD-kártya ikon** | váltás a tárolók között (belső, SD-kártya, USB) |
| ⬆ **nyíl** | egy szinttel feljebb |

### Oszlopok és rendezés

A fejlécben **Név · Szerző · Méret · Dátum** — bármelyikre koppintva aszerint
rendez; újra koppintva megfordítja a sorrendet. Az aktív oszlop színnel és
▲▼ nyíllal van jelölve.

Minden sor két részből áll: fent a fájlnév, méret, dátum; alatta — ha a
katalógusban megvan — **zölden a szerző és a cím**.

### Szkennelés

A radar ikon végigjárja az aktuális mappát és minden almappáját, összegyűjti
a könyvfájlokat, és párosítja őket a katalógussal. Közben látod, hány fájlnál
tart és mennyit párosított; a „Mégse” gombbal megszakítható.

A szkennelés **inkrementális**: a változatlan fájlokat nem dolgozza fel újra,
így a második futás sokkal gyorsabb.

### Gyors mozgás

A lista jobb szélén **gyorsgörgető sáv** van — húzva több tízezer soron is
azonnal átszaladsz.

## 6. Könyv részletei

Egy fájlra **egyszer koppintva** nyílik. Itt látod:

- cím, szerző, és a katalógusból minden metaadat (kiadó, év, ISBN, sorozat,
  címkék),
- a **fülszöveget / leírást**,
- a könyv **első pár ezer karakterét** előnézetként,
- a haladásodat, ha már elkezdted.

Gombok: **Folytatás** (vagy Felolvasás), **Elölről**, **Megosztás** (a teljes
szöveg átadása másik TTS appnak), és **Olvasás képernyőn**.

## 7. Olvasó képernyő

Ez az app szíve: **itt olvasol és itt hallgatsz is** — nincs külön lejátszó.

Megnyitható: hosszú nyomással a böngészőben, a részletezőből, az olvasási
listából, vagy az értesítésre koppintva.

### Felső sáv

| Ikon | Funkció |
|---|---|
| 🔍 | keresés a szövegben (ékezet-független) |
| 🔖+ | könyvjelző hozzáadása |
| 🔖 | könyvjelzők listája |
| ⏹ | felolvasás teljes leállítása (csak ha épp szól) |

### Alsó vezérlősáv

| ⏫ | ⬆ | ⏮ | ▶/⏸ | ⏭ | ⬇ | ⏬ |
|---|---|---|---|---|---|---|
| fejezet vissza | képernyő vissza | mondat vissza | lejátszás/szünet | mondat előre | képernyő előre | fejezet előre |

Alatta:

- **A− / A＋** — betűméret (megjegyzi),
- **pozíció-csúszka** — ugrás a könyv bármely pontjára, százalékkijelzéssel,
- 🎯 **követés** — ha be van kapcsolva, a szöveg magától gördül a felolvasott
  résszel (ha te görgetsz, nem ugrik el a kezed alól),
- 🎚 **hangolás** — sebesség (0,5×–3×) és hangmagasság csúszkák.

Az információs sor mutatja, hányadik bekezdésnél jársz, és ha szól a
felolvasás, a százalékot meg a hallgatással töltött időt is.

### Gesztusok a szövegen

- **Dupla koppintás** → a felolvasás **pontosan attól a mondattól** indul,
  amelyikre böktél.
- **Hosszú nyomás** → könyvjelző az adott bekezdéshez (🔖 jelenik meg előtte).

Az éppen felolvasott mondat **kiemelve** látszik, a bekezdése halványan
színezett — így szemmel is követheted.

### Keresés a szövegben

A 🔍 ikonnal nyílik. Legalább 2 karakter után keres, **ékezet-függetlenül**
(a „varazslono” megtalálja a „varázslónő”-t). A találatok kiemelve, a
számláló mutatja, hányadiknál jársz (pl. `3/17`), a ▲▼ gombokkal ugrálhatsz.

### Könyvjelzők

Hosszú nyomás bárhol a szövegen, vagy a 🔖+ gomb. Ha épp szól a felolvasás,
a jelző **a felolvasott helyre** kerül, nem oda, ahol nézelődsz.

A 🔖 gombbal megnyíló listában látod a bekezdés számát, dátumot és egy
részletet; koppintásra odaugrik, a kuka ikonnal törölhető.

### Fejezetek

A fejezetléptetés formátumtól függően működik:

- **epub** — a valódi fejezetfájlok és a címsorok alapján,
- **mobi, html** — a címsorok (`h1`–`h6`) alapján,
- **fb2** — a szekciócímek alapján,
- **txt, rtf, pdf, docx** — címsor-heurisztikával („12. fejezet”, római
  számok, csupa nagybetűs sorok).

Ha egy könyvben semmilyen fejezetjelet nem talál, a gomb ~5%-os ugrásra vált,
hogy sose legyen használhatatlan.

## 8. Fülhallgató-gombok

Működik Bluetooth-os és vezetékes fülhallgatóval is, az egygombos típusokkal:

| Nyomás | Hatás |
|---|---|
| **1×** | Start / Stop (lejátszás ⇄ szünet) |
| **2×** | ~5 másodperc vissza |
| **3×** | szintén visszaugrás |

A „5 másodperc” becslés: a TTS-nek nincs valódi idővonala, ezért az app a
beállított beszédsebességből számolja át, és mondathatárra igazít.

A gombvezérlés addig él, amíg a felolvasó fut vagy szünetel (látszik az
értesítés). Ha az ⏹ Stop gombbal teljesen leállítod, a fülhallgató gombja már
nem indítja újra — ez szándékos, hogy ne szólaljon meg váratlanul a zsebedben.

## 9. Olvasási lista és exportálás

A böngésző 📊 ikonjával nyílik.

Felül négy szám: **Elkezdett · Elolvasott · Folyamatban · összes hallgatási
idő**. Alatta két szekció:

- **📖 Folyamatban** — amiket elkezdtél, de még nincsenek kész,
- **✔ Elolvasott** — amik 98% fölött vannak.

Minden könyvnél haladás-csík, pontos százalék, hallgatási idő, utolsó dátum,
és három gomb: **▶ felolvasás**, **📖 olvasás**, **🗑 törlés a listából**.

A haladásba a képernyős olvasás és a felolvasás közül a **nagyobb** számít.

### Exportálás

A fejlécben két gomb:

- **💾 Mentés** — kiírja a nyilvántartást a `Download/KonyvtarTTS/` mappába:
  - `olvasas_<dátum>.csv` — státusz, cím, szerző, készültség %, bekezdés,
    hallgatott percek, utolsó alkalom, fájlútvonal, könyv ID
  - `konyvjelzok_<dátum>.csv` — minden könyvjelző a szövegrészlettel
  - `konyvtar_tts_<dátum>.db` — az app teljes adatbázisának másolata
- **📤 Megosztás** — ugyanezt lefuttatja, majd a CSV-ket átadja e-mailnek,
  felhőnek, bárminek.

A CSV **pontosvesszős, UTF-8 BOM-mal**, hogy a magyar Excel és a LibreOffice
dupla kattintásra, ékezethelyesen nyissa meg. A `.db` másolat SQLite, tehát
PC-n bármilyen eszközzel (pl. Python `sqlite3`) lekérdezhető.

## 10. Beállítások

- **Katalógus-adatbázis** — az aktuális fájl útvonala és állapota, csere.
- **Könyvek gyökérmappája** — hol nyíljon a böngésző induláskor.
- **Katalógus építése a könyvekből** — lásd a [10/a](#10a-katalógus-építése-a-könyvekből) pontot.
- **Gyorsítótárak** — hány fájlt szkenneltél, mennyi kinyert szöveg van
  tárolva, és mindkettő törölhető. (A törlés a **pozícióidat nem** bántja.)
- **Szövegfelolvasó** — közvetlen ugrás a rendszer TTS-beállításaihoz, ahol
  motort és hangot válthatsz.

## 10/a. Katalógus építése a könyvekből

Ha nincs kész katalógusod, az app **maga készít egyet** — a könyvfájlok
belsejében tárolt metaadatokból, internet nélkül.

**Használat:** Beállítások → *Katalógus építése a könyvekből* → **Építés**.
Forrásként az aktuális gyökérmappát járja végig (rekurzívan), az eredmény
pedig ide kerül: `Download/KonyvtarTTS/sajat_katalogus.db`. Az építés végén
egy gombbal azonnal használatba is veheted.

**Újrafuttatás új könyvek után:** a gomb ilyenkor már *Frissítés az új
könyvekkel* feliratú. **A meglévő bejegyzéseket nem bántja** — útvonal
szerint felismeri, mi van már benne, és csak az újakat dolgozza fel. Így a
második futás sokkal gyorsabb, és amit esetleg kézzel vagy a PC-n javítottál
az adatbázisban, az megmarad.

### Mit talál meg formátumonként

| Formátum | Mit nyer ki |
|---|---|
| **EPUB** | cím, szerző, kiadó, év, ISBN, nyelv, **fülszöveg**, sorozat + kötetszám, címkék |
| **FB2** | szerző, cím, **annotáció**, műfaj, sorozat, kiadó, év, ISBN |
| **MOBI/AZW3** | cím, szerző, kiadó, **leírás**, ISBN, tárgyszavak, dátum |
| **DOCX** | cím, szerző, leírás, kulcsszavak |
| **RTF** | cím, szerző, tárgy, megjegyzés (ha ki van töltve) |
| **PDF** | cím, szerző, kulcsszavak — **kapcsolható**, mert lassabb |
| **TXT és a többi** | a fájlnévből kitalált cím és szerző |

A **PDF-kapcsoló** azért van külön, mert a PDF-eknél a metaadat beolvasásához
végig kell nézni a fájl szerkezetét, ami nagy fájloknál lassú. Ráadásul a
PDF-ek „címe" gyakran szemét (a szkennelő program neve, fájlnév, „Microsoft
Word - valami.doc"), ezért az app kiszűri az ilyen nyilvánvalóan használhatatlan
értékeket, és inkább a fájlnévre támaszkodik.

### Amit tudni érdemes

- **Fülszöveget nem tud varázsolni.** Ahol a fájl nem tartalmaz leírást
  (tipikusan szkennelt PDF, TXT), ott a szinopszis üres marad.
- **Duplikátumok összevonása:** ha ugyanaz a könyv megvan epubban és mobiban
  is, egyetlen katalógusbejegyzés jön létre, két fájllal — a cím és a szerző
  ékezet-független összevetése alapján.
- **A séma azonos** a PC-n készült katalóguséval (`konyvek` +
  `fizikai_fajlok`), ezért a fájl a gépeden bármilyen SQLite-eszközzel
  megnyitható, és az app is ugyanúgy kezeli.
- **Rossz metaadat a fájlban:** előfordul, hogy egy könyvben fel van cserélve
  a cím és a szerző, vagy hiányos. Ilyenkor az app azt írja be, ami a fájlban
  van — ezt Calibre-vel tudod javítani a forrásfájlban, majd újraépíteni.
- **A régi bejegyzések megmaradnak.** Ha törölni akarsz belőle, töröld az
  egész `sajat_katalogus.db`-t, és építsd újra.

## 11. Hibaelhárítás

**„Nincs DB” pirossal a fejlécben**
A katalógus nem található. Beállítások → Adatbázisfájl kiválasztása. Ellenőrizd,
hogy tényleg a `.db` fájlt másoltad-e fel (ne a `-wal`/`-shm` fájlokat).

**Nem látom az SD-kártyát**
Az útvonal-sor elején lévő 💾 ikonnal válts tárolót. Ha nem jelenik meg a
kártya, a rendszer nem csatolta fel.

**„Ez a könyv DRM-védett”**
A Mobipocket-titkosítású fájlokat nem tudja megnyitni. Konvertáld (pl.
Calibre-vel) egy szabad formátumra.

**„A PDF nem tartalmaz szövegréteget”**
Szkennelt, képalapú PDF. OCR-rel kell szöveget csinálni belőle, utána `.txt`
formában felolvasható.

**A `.doc` fájlok nem szólalnak meg**
A régi bináris `.doc` nem támogatott — konvertáld `.docx`-re vagy `.txt`-re.

**A felolvasás elhallgat egy idő után**
Az akkumulátor-optimalizálás leállíthatta a szolgáltatást. A rendszer
beállításaiban vedd ki az appot az energiatakarékos korlátozás alól
(Beállítások → Alkalmazások → Könyvtár TTS → Akkumulátor → Korlátlan).

**Nincs magyar hang**
Beállítások → Szövegfelolvasó → a rendszer TTS-beállításainál töltsd le a
magyar nyelvi csomagot a Google Szövegfelolvasóhoz.

**Lassú az első megnyitás**
Az első alkalommal az app kinyeri a teljes szöveget a könyvből (nagy PDF-nél
ez eltarthat egy ideig). Utána gyorsítótárból jön, és azonnali.
