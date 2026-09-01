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
4. [A könyvtár beolvasása](#4-a-könyvtár-beolvasása)
5. [Könyvtár — a nyitóképernyő](#5-könyvtár--a-nyitóképernyő)
6. [Fájlböngésző](#6-fájlböngésző)
7. [A helyi menü](#7-a-helyi-menü)
8. [Polc](#8-polc)
9. [Olvasó képernyő](#9-olvasó-képernyő)
10. [Fülhallgató-gombok](#10-fülhallgató-gombok)
11. [Olvasási lista és exportálás](#11-olvasási-lista-és-exportálás)
12. [Beállítások](#12-beállítások)
13. [Hibaelhárítás](#13-hibaelhárítás)

---

## 1. Mire lesz szükséged

- **Android 11 vagy újabb** telefon.
- **Rendszer TTS motor** magyar hanggal. A legtöbb telefonon a Google
  Szövegfelolvasó eleve fent van; ha nincs magyar hang, egy gombbal
  letölthető az app beállításaiból.
- **Könyvek a telefonon**, egy mappában — almappákkal együtt jó, és mehet
  SD-kártyára is.

Nem kell hozzá fiók, internet vagy előre elkészített adatbázis.

## 2. Telepítés

A legegyszerűbb a **[letöltőoldal](https://sanyi1963bp.github.io/Vox-Libris/docs/)**:
telefonról megnyitva egy gombbal települ.

Kézzel: töltsd le az APK-t a
[Releases](https://github.com/sanyi1963bp/Vox-Libris/releases) oldalról, és
nyisd meg a telefonon. A rendszer figyelmeztet, hogy ismeretlen forrásból
származó alkalmazás — ez normális, mert nem a Play Áruházból jön; engedélyezni
kell a telepítést annak az appnak, amivel megnyitottad.

## 3. Első indítás — engedélyek

Az app **egyetlen érdemi engedélyt** kér: **„Minden fájl kezelése"**. Enélkül
nem látja a könyveidet. A gomb a rendszerbeállításokba visz, ott kell
bekapcsolni.

Android 13-tól kér **értesítési engedélyt** is. Enélkül is működik minden,
csak nem lesz vezérlősáv a lezárt képernyőn.

**Internet-engedélyt nem kér, és nem is tud kérni** — nincs a manifestben.
Minden adat a telefonon marad.

## 4. A könyvtár beolvasása

Első indításkor az app megkérdezi, **hol vannak a könyveid**, majd felajánlja,
hogy beolvassa őket. Ez két menetben történik:

**Első menet — metaadatok.** Végigjárja a mappát és minden almappáját,
kiolvassa a fájlokból a címet, szerzőt, fülszöveget, kiadót, sorozatot, ISBN-t
és a címkéket, és katalógust épít belőlük. Ezer könyv körülbelül egy perc.
Ennek a végén a könyvtárad már használható.

**Második menet — borítók.** A háttérben indul, és kinyeri a borítóképeket a
fájlokból. Ez lassabb (képet kell dekódolni, PDF-nél oldalt kirajzolni), ezért
fut külön — közben nyugodtan használhatod az appot.

### Újrafuttatás

Új könyvek bemásolása után: **Beállítások → Katalógus → Könyvtár beolvasása**.
A beolvasás **inkrementális** — a meglévő bejegyzésekhez nem nyúl, csak az
újakat veszi fel. Ugyanígy a borítóknál: amit egyszer kinyert, azt nem
próbálja újra.

### Hol van a katalógus

```
Download/KonyvtarTTS/sajat_katalogus.db
```

Szándékosan **látható fájl**, nem az app rejtett tárhelyén: túléli az app
újratelepítését, és PC-n bármilyen SQLite-eszközzel megnyitható. Ha törlöd,
csak egy új beolvasás kell — az olvasási haladásod nem ebben van.

### Amit tudni érdemes

- **Fülszöveget nem tud varázsolni.** Ahol a fájl nem tartalmaz leírást
  (tipikusan szkennelt PDF, TXT), ott üres marad.
- **Duplikátumok összevonása:** ha ugyanaz a könyv megvan epubban és mobiban
  is, egyetlen katalógusbejegyzés jön létre, két fájllal — a cím és a szerző
  ékezet-független összevetése alapján.
- **Rossz metaadat a fájlban:** előfordul, hogy egy könyvben fel van cserélve
  a cím és a szerző, vagy hiányos. Az app azt írja be, ami a fájlban van — ezt
  Calibre-vel tudod javítani a forrásfájlban, majd újraépíteni.

## 5. Könyvtár — a nyitóképernyő

Az összes könyved egy listában. Jobbra-balra **pöccintve** átvált a
[fájlböngészőre](#6-fájlböngésző); a cím melletti két pötty mutatja, hol
állsz.

### Keresés

A kereső egyszerre nézi a **címet, a szerzőt, a fájlnevet és a saját
jegyzeteidet**, ékezettől függetlenül — a *jozsef* megtalálja a *József*-et.
Gépelés közben azonnal frissül, több ezer könyvnél is.

### Betűsáv

A kereső alatt. Egy koppintás a kezdőbetűre, és csak az azzal kezdődő könyvek
maradnak; ugyanarra a betűre újra koppintva megszűnik a szűrés.

A sáv **csak azokat a betűket mutatja, amikhez tényleg van könyved** — nincs
üresbe vezető gomb. A rendezéstől függően a cím vagy a szerző kezdőbetűjét
nézi, és az ékezetes betűk az alapbetűhöz sorolódnak (az Á az A-nál van).

> A betűsáv maga is vízszintesen görgethető, ezért ott a pöccintés először azt
> mozdítja. Ha nézetet akarsz váltani, a lista területén pöccints.

### Rendezés és formátumszűrő

A kereső mellett két ikon:

| Ikon | Mit csinál |
|---|---|
| **rendezés** | cím, szerző vagy formátum szerint; újra választva megfordítja a sorrendet |
| **szűrő** | formátumok darabszámmal (EPUB 2100, PDF 900…), koppintásra szűkít |

A formátumszűrő egyben **áttekintés is**: ebből látod, miből mennyi van a
telefonodon.

### Egy sor felépítése

Balról a **formátumjelvény**, mellette a cím, alatta a szerző és a fájlnév.
Ha elkezdted a könyvet, alul **zöld haladás-csík**. Ha van hozzá jegyzeted, a
cím mellett egy kis ✎ jel.

**A szürke formátumjelvény azt jelenti, hogy abból a fájlból nem tudunk
szöveget kinyerni** — az a könyv nem fog megszólalni.

### Koppintások

| Gesztus | Mit csinál |
|---|---|
| **egy koppintás** | kijelöli a sort |
| **dupla koppintás** | megnyitja **és felolvassa** az utolsó pozíciótól |
| **hosszú nyomás** | [helyi menü](#7-a-helyi-menü) |

### Felső sáv

| Ikon | Funkció |
|---|---|
| **polc** | lapozható borítónézet |
| **mappa** | átlapoz a fájlböngészőre |
| **oszlopdiagram** | olvasási lista és statisztika |
| **fogaskerék** | beállítások |

Alatta egy sorban: hány könyvet mutat a szűrés az összesből, jobbra pedig az
**Elolvasva / Folyamatban** számlálók — koppintásra megnyílik a lista.

## 6. Fájlböngésző

Ugyanazok a könyvek, mappák szerint. Sűrű, ikonmentes lista, hogy egy
képernyőre minél több sor férjen.

### Felső sáv

| Elem | Mit csinál |
|---|---|
| **könyv ikon** | visszalapoz a könyvtárra |
| **radar** | könyvtár beolvasása |
| **oszlopdiagram** | olvasási lista és statisztika |
| **fogaskerék** | beállítások |
| **keresőmező** | szűrés fájlnév, cím és szerző szerint |
| **„Almappák is"** | a keresés az egész fanézetre kiterjed, nem csak egy szintre |
| **SD-kártya ikon** | váltás a tárolók között (belső, SD-kártya, USB) |
| **⬆ nyíl** | egy szinttel feljebb |

### Oszlopok és rendezés

A fejlécben **Név · Szerző · Méret · Dátum** — bármelyikre koppintva aszerint
rendez; újra koppintva megfordítja a sorrendet.

Minden sor két részből áll: fent a fájlnév, méret, dátum; alatta — ha a
katalógusban megvan — a szerző és a cím.

### Koppintások

| Gesztus | Mit csinál |
|---|---|
| **egy koppintás** | mappa megnyitása, vagy a könyv megnyitása olvasásra |
| **dupla koppintás** | megnyitás **és felolvasás** |
| **hosszú nyomás** | [helyi menü](#7-a-helyi-menü) |

A sor végén lévő **ⓘ** gomb közvetlenül az adatlapot nyitja.

### Gyors mozgás

A lista jobb szélén **gyorsgörgető sáv** van — húzva több tízezer soron is
azonnal átszaladsz.

## 7. A helyi menü

**Hosszú nyomásra jön elő, mindkét nézetben ugyanaz.**

| Pont | Mit csinál |
|---|---|
| **Adatlap** | borító, leírás, minden metaadat, hol tartasz a könyvben |
| **Jegyzet** | saját jegyzet a könyvhöz (üresen mentve törlődik) |
| **Átnevezés** | a fájl saját mappáján belül |
| **Áthelyezés** | másik mappába, kis mappaböngészővel |
| **Másolás** | másik mappába; az eredeti marad |
| **Törlés** | végleges — mindig rákérdez, és mutatja a fájl nevét |

### Miért az appban és ne fájlkezelőben

Ez a menü nem csak a fájlt mozgatja: **minden hozzá kötött adat is követi**
— a katalógusbejegyzés, az olvasási haladás, a könyvjelzők, a jegyzet és a
bélyegkép.

Ha ugyanezt egy fájlkezelőben csinálod, mindez ott marad a régi útvonalon, és
a könyv **újként bukkan fel, nulláról**.

### A törlésről

Nincs visszavonás és nincs kuka. A megerősítő ablak megmutatja a fájl nevét —
olvasd el, mielőtt rábólintasz.

### Az olvasóban

Az épp olvasott könyvnél **nincs fájlművelet**: nem nevezzük át és nem
töröljük magad alól, mert az eltörné a felolvasás útvonalát és a
pozíciómentést. **Jegyzetet viszont ott is írhatsz** — sőt, olvasás közben az
a leghasznosabb.

## 8. Polc

Lapozható borítónézet: úgy nézegetheted a könyveket, mint a polc előtt állva.
A felső sáv **polc ikonjával** nyílik.

Azt mutatja, amit a **lista éppen szűr** — ha rákerestél valamire vagy
leszűkítetted egy betűre, a polcon is csak azok lapozhatók.

A borító alatt cím, szerző és haladás-csík. Koppintás megnyitja a könyvet,
hosszú nyomás az adatlapját.

Ahol a fájlból nem sikerült borítót kinyerni, ott a címből és a szerzőből
**rajzolt borító** látszik — a szín a címből származik, tehát ugyanaz a könyv
mindig ugyanolyan.

## 9. Olvasó képernyő

Ez az app szíve: **itt olvasol és itt hallgatsz is** — nincs külön lejátszó.

Megnyitható a listából vagy a böngészőből dupla koppintással, az adatlapról,
az olvasási listából, a „most szól" sávra koppintva, vagy az értesítésről.

### Felső sáv

| Ikon | Funkció |
|---|---|
| **⬅** | vissza |
| **🔍** | keresés a szövegben (ékezet-független) |
| **fogaskerék** | beállítások |
| **⋮** | könyvjelző ide · könyvjelzők listája · adatlap · felolvasás leállítása |

### Alsó vezérlősáv

Legfelül egy állapotsor: hányadik bekezdésnél jársz, és ha szól a felolvasás,
a fejezet, a százalék és a hallgatással töltött idő is. Mellette két kapcsoló:

- 🎯 **követés** — a szöveg magától gördül a felolvasott résszel (ha te
  görgetsz, nem ugrik el a kezed alól),
- 🎚 **hangolás** — kinyitja a betűméret, a sebesség (0,5×–3×) és a
  hangmagasság állítását.

Alatta a **pozíció-csúszka** százalékkijelzéssel, legalul pedig a léptetés:

| ⏫ | ⏮ | ◀ | ▶/⏸ | ▶ | ⏭ | ⏬ |
|---|---|---|---|---|---|---|
| fejezet vissza | bekezdés vissza | mondat vissza | lejátszás/szünet | mondat előre | bekezdés előre | fejezet előre |

### Gesztusok a szövegen

- **Dupla koppintás** → a felolvasás **pontosan attól a mondattól** indul,
  amelyikre böktél.
- **Hosszú nyomás** → könyvjelző az adott bekezdéshez (🔖 jelenik meg előtte).

Az éppen felolvasott mondat **kiemelve** látszik, a bekezdése halványan
színezett — így szemmel is követheted.

### Keresés a szövegben

A 🔍 ikonnal nyílik. Legalább 2 karakter után keres, **ékezet-függetlenül**
(a „varazslono" megtalálja a „varázslónő"-t). A találatok kiemelve, a
számláló mutatja, hányadiknál jársz (pl. `3/17`), a ▲▼ gombokkal ugrálhatsz.

### Könyvjelzők

Hosszú nyomás bárhol a szövegen, vagy a ⋮ menü *Könyvjelző ide* pontja. Ha
épp szól a felolvasás, a jelző **a felolvasott helyre** kerül, nem oda, ahol
nézelődsz.

A listában látod a bekezdés számát, a dátumot és egy részletet; koppintásra
odaugrik, a kuka ikonnal törölhető.

### Fejezetek

A fejezetléptetés formátumtól függően működik:

- **epub** — a valódi fejezetfájlok és a címsorok alapján,
- **mobi, html** — a címsorok (`h1`–`h6`) alapján,
- **fb2** — a szekciócímek alapján,
- **txt, rtf, pdf, docx** — címsor-heurisztikával („12. fejezet", római
  számok, csupa nagybetűs sorok).

Ha egy könyvben semmilyen fejezetjelet nem talál, a gomb ~5%-os ugrásra vált,
hogy sose legyen használhatatlan.

A fejezethatárokat a szövegben **vérvörös sáv** jelzi, és felolvasáskor
mélyebb kettős hang szól előttük (kikapcsolható).

### „Most szól" sáv

Amíg felolvasás megy, **minden más képernyő alján** ott a könyv címe és a
haladás. A gombbal bárhonnan elnémítható, a sávra koppintva pedig
visszaugrasz a könyvhöz — oda, ahol tart.

## 10. Fülhallgató-gombok

Működik Bluetooth-os és vezetékes fülhallgatóval is, az egygombos típusokkal:

| Nyomás | Hatás |
|---|---|
| **1×** | Start / Stop (lejátszás ⇄ szünet) |
| **2×** | ~5 másodperc vissza |
| **3×** | szintén visszaugrás |

A „5 másodperc" becslés: a TTS-nek nincs valódi idővonala, ezért az app a
beállított beszédsebességből számolja át, és mondathatárra igazít.

A gombvezérlés addig él, amíg a felolvasó fut vagy szünetel (látszik az
értesítés). Ha a **Stop** gombbal teljesen leállítod, a fülhallgató gombja
már nem indítja újra — ez szándékos, hogy ne szólaljon meg váratlanul a
zsebedben.

## 11. Olvasási lista és exportálás

A felső sáv 📊 ikonjával nyílik, vagy a könyvtár tetején lévő számlálókra
koppintva.

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

## 12. Beállítások

Kártyák egymás alatt:

| Kártya | Mit állít |
|---|---|
| **Könyvek gyökérmappája** | hol keresse a könyveidet a beolvasás |
| **Katalógus** | hány mű és hány fájl van benne, a fájl helye, PDF-ek beolvasása, **Könyvtár beolvasása**, hiányzó fájlok eltávolítása |
| **Borítók** | hány borító van és mennyi helyet foglal, **Borítók betöltése**, törlés, és a **Borítók a listában** kapcsoló (alapból ki) |
| **Megjelenés** | világos/sötét/rendszerkövető téma, hat színséma, a felület betűmérete |
| **Felület nyelve** | tíz nyelv, vagy rendszerkövetés |
| **Felolvasás nyelve** | a telepített TTS motor bármelyik nyelve, és egy gomb a hangletöltőhöz |
| **Hangjelzések** | fejezetjelző hang be/ki, hangereje |
| **Olvasás és vezérlés** | követés, képernyő ébren tartása, a fülhallgató dupla nyomásának ugrása |
| **Gyorsítótár** | mennyi kinyert szöveg van tárolva, törölhető (a pozícióidat nem bántja) |
| **Szövegfelolvasó motor** | ugrás a rendszer TTS-beállításaihoz |

> **A felület nyelve külön áll a felolvasás nyelvétől.** Olvashatsz magyar
> felülettel angol könyvet, vagy fordítva.

## 13. Hibaelhárítás

**A lista üres, pedig vannak könyveim**
Még nem futott le a beolvasás. Beállítások → Katalógus → Könyvtár beolvasása.
Ellenőrizd azt is, hogy a gyökérmappa jó helyre mutat-e.

**Nem látok borítókat, csak színes helyettesítőket**
A borítók a második menetben töltődnek. Beállítások → Borítók → Borítók
betöltése. Ahol a fájl nem tartalmaz borítót (TXT, RTF, DOCX), ott marad a
rajzolt.

**Nem látom az SD-kártyát**
A böngészőben a 💾 ikonnal válts tárolót. Ha nem jelenik meg a kártya, a
rendszer nem csatolta fel.

**„Ez a könyv DRM-védett"**
A Mobipocket-titkosítású fájlokat nem tudja megnyitni. Konvertáld (pl.
Calibre-vel) egy szabad formátumra.

**„A PDF nem tartalmaz szövegréteget"**
Szkennelt, képalapú PDF. OCR-rel kell szöveget csinálni belőle, utána `.txt`
formában felolvasható.

**A `.doc` fájlok nem szólalnak meg**
A régi bináris `.doc` nem támogatott — konvertáld `.docx`-re vagy `.txt`-re.
A listában ezért szürke a jelvényük.

**A felolvasás elhallgat egy idő után**
Az akkumulátor-optimalizálás leállíthatta a szolgáltatást. A rendszer
beállításaiban vedd ki az appot az energiatakarékos korlátozás alól
(Beállítások → Alkalmazások → Könyvtár TTS → Akkumulátor → Korlátlan).

**Nincs magyar hang**
Beállítások → Felolvasás nyelve → *Hangok letöltése*, vagy a rendszer
TTS-beállításaiban töltsd le a magyar nyelvi csomagot.

**Lassú az első megnyitás**
Az első alkalommal az app kinyeri a teljes szöveget a könyvből (nagy PDF-nél
ez eltarthat egy ideig). Utána gyorsítótárból jön, és azonnali.

**A pöccintés nem vált nézetet**
Valószínűleg a betűsávon pöccintesz, ami maga is görgethető. A lista
területén próbáld.

**Átneveztem egy fájlt, és eltűnt a haladásom**
Ha az appon kívül, fájlkezelőben nevezted át, akkor igen — az app a régi
útvonalon keresi. Használd a [helyi menüt](#7-a-helyi-menü), az mindent
átvezet.
