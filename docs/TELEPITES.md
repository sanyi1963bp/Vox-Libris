# Vox Libris — telepítés és függőségek

🇭🇺 Magyar (ez a lap) · 🇬🇧 [English](SETUP.en.md) · ⬅ [Vissza a főoldalra](../README.md)

Ez a lap mindent végigvesz, amire szükség van: a telefonos használathoz, a
Bluetooth-hoz, és a forrásból való fordításhoz.

---

## Tartalom

**A) A telefonon — a használathoz**
1. [Mit igényel az app](#a1-mit-igényel-az-app)
2. [Az APK telepítése](#a2-az-apk-telepítése)
3. [Engedélyek](#a3-engedélyek)
4. [Szövegfelolvasó motor és a magyar hang](#a4-szövegfelolvasó-motor-és-a-magyar-hang)
5. [Más nyelvek hangjai](#a5-más-nyelvek-hangjai)
6. [Akkumulátor-optimalizálás kikapcsolása](#a6-akkumulátor-optimalizálás-kikapcsolása)
7. [A katalógus és a könyvek felmásolása](#a7-a-katalógus-és-a-könyvek-felmásolása)

**B) [Bluetooth és fülhallgató](#b-bluetooth-és-fülhallgató)**

**C) [Fejlesztéshez — fordítás forrásból](#c-fejlesztéshez--fordítás-forrásból)**

---

# A) A telefonon

## A.1 Mit igényel az app

| Követelmény | Részlet |
|---|---|
| **Android 11 (API 30) vagy újabb** | Az app a modern tárolókezelést és az előtér-szolgáltatásokat használja. |
| **Szövegfelolvasó (TTS) motor** | Külön alkalmazás, nem az app része. Lásd az [A.4](#a4-szövegfelolvasó-motor-és-a-magyar-hang) pontot. |
| **~30 MB szabad hely** | Az app maga ~9 MB; a többi a kinyert szövegek gyorsítótára (könyvenként pár száz KB). |
| **Semmi más** | Nincs szükség internetre, fiókra, Google Play Szolgáltatásokra vagy root-ra. |

> Az app **nem kér internet-hozzáférést**. A TTS motor viszont külön alkalmazás,
> annak lehet saját hálózati igénye — erről az [A.4](#a4-szövegfelolvasó-motor-és-a-magyar-hang) pontban.

## A.2 Az APK telepítése

1. Töltsd le a `vox-libris-<verzió>.apk` fájlt a
   [Releases](https://github.com/sanyi1963bp/Vox-Libris/releases) oldalról,
   vagy másold át a PC-ről USB-kábellel.
2. Nyisd meg a telefon fájlkezelőjében és koppints rá.
3. A rendszer megkérdezi, hogy megbízol-e a forrásban: engedélyezd az
   „ismeretlen alkalmazások telepítését" **annak az alkalmazásnak** (fájlkezelő
   vagy böngésző), ahonnan indítod. Ez egyszeri lépés.
4. **Frissítéskor** ugyanígy telepítsd rá az újat — az adataid (olvasási
   pozíciók, könyvjelzők, szkennelés) megmaradnak.

## A.3 Engedélyek

Az app három engedélyt használ, mind az első indításkor kérhető:

| Engedély | Mire kell | Hol adható meg |
|---|---|---|
| **Minden fájl kezelése**<br>(`MANAGE_EXTERNAL_STORAGE`) | Kötelező. Enélkül nem látja a könyveidet és a katalógust. | Az app nyitóképernyőjén a gomb a rendszerbeállításokba visz. |
| **Értesítések** | A felolvasás vezérlői az értesítési sávban és a lezárt képernyőn. Android 13-tól kell. | Felugró ablak az első indításkor. |
| **Akkumulátor: korlátlan** | Hogy hosszú felolvasás közben a rendszer ne állítsa le. Nem kötelező, de erősen ajánlott. | Lásd az [A.6](#a6-akkumulátor-optimalizálás-kikapcsolása) pontot. |

Az app **nem kér** helymeghatározást, névjegyeket, kamerát, mikrofont és
internetet.

## A.4 Szövegfelolvasó motor és a magyar hang

A felolvasáshoz a **rendszer TTS motorját** használjuk — ez egy különálló
alkalmazás, amit egyszer kell beállítani, és utána minden felolvasó app
használja.

### Google Szövegfelolvasó (ajánlott)

Ez tudja a legjobb magyar hangot, és a legtöbb telefonon már fent van.

1. **Telepítés (ha hiányzik):** Play Áruház → keresd:
   *Speech Recognition & Synthesis* (magyarul *Beszédfelismerés és -szintézis*,
   régebben *Google Szövegfelolvasó*). Csomagnév: `com.google.android.tts`.
2. **Beállítás alapértelmezettként:**
   - **Samsung telefonon:** Beállítások → Általános kezelés → **Szöveg
     felolvasása** (*Text-to-speech*)
   - **Más telefonon:** Beállítások → Kisegítő lehetőségek → **Szövegfelolvasó
     kimenete**
   - Vagy egyszerűbben: az appban **Beállítások → „Rendszer TTS beállítások…"**
     gomb, ami közvetlenül ide visz.
3. Az **Előnyben részesített motor** legyen a Google.
4. **Nyelv:** válaszd a **magyart**. Ha a nyelv mellett letöltés ikon van,
   koppints rá — ez tölti le az offline hangot (néhány tíz MB).
5. **Próba:** a „Lejátszás" / „Hallgassa meg a példát" gombbal ellenőrizd.
6. **Sebesség és hangmagasság:** ezeket **ne** itt állítsd — az appban van
   saját csúszka (🎚 hangolás gomb az olvasóban), az felülírja a rendszerét.

> **Offline használat:** ha a hangot letöltötted, a felolvasás internet nélkül
> is működik. Egyes Google-hangok („természetes", neurális minőség) hálózatot
> igényelnek — ha metrón, dugóban is hallgatnád, maradj a letölthető
> offline hangnál.

### Alternatív motorok

| Motor | Megjegyzés |
|---|---|
| **Samsung TTS** | Samsung telefonokon gyárilag ott van, de a magyar hang nem minden készüléken/régióban érhető el. Ha nincs magyar a listában, válts a Google motorra. |
| **eSpeak NG** | Ingyenes, nyílt forrású, teljesen offline, apró. A hangja gépies, de magyarul is beszél — jó tartalék, ha semmi más nem elérhető. |
| **Kereskedelmi motorok** | Léteznek fizetős, természetesebb hangú motorok is; ezek a rendszerbeállításokban ugyanúgy kiválaszthatók, és az app automatikusan azt használja, ami alapértelmezett. |

Az app **nem köti magát egyik motorhoz sem**: azt használja, ami a
rendszerben be van állítva. Ha motort váltasz, elég az appot újraindítani.

## A.5 Más nyelvek hangjai

Az app **először magyar hangot keres**, és csak ha nincs, akkor vált a telefon
rendszernyelvére.

Idegen nyelvű könyvhöz tehát:

1. Töltsd le a kívánt nyelvet a TTS motor beállításaiban (ugyanott, ahol a
   magyart: nyelv kiválasztása → letöltés).
2. Állítsd át a **rendszer TTS nyelvét** arra a nyelvre (a motor
   beállításaiban), vagy vedd le a magyar hangot.
3. Indítsd újra az appot.

> **Őszintén a korlátról:** jelenleg nincs könyvenkénti nyelvválasztó az
> appban — ha van magyar hang telepítve, mindig azt használja. Egy angol
> könyvet magyar hanggal felolvastatni élvezhetetlen, ezért ilyenkor a fenti
> kézi váltás kell. Ha ez zavaró, nyiss egy Issue-t: könyvenkénti
> nyelvbeállítás beépíthető.

## A.6 Akkumulátor-optimalizálás kikapcsolása

Hosszú felolvasásnál a rendszer energiatakarékossága leállíthatja a
szolgáltatást. Érdemes kivenni az appot a korlátozás alól:

- **Samsung:** Beállítások → Akkumulátor és eszközkarbantartás → Akkumulátor →
  **Háttérhasználati korlátok** → *Sosem alvó alkalmazások* → add hozzá a
  **Könyvtár TTS**-t.
- **Általánosan (bármely Android):** Beállítások → Alkalmazások → **Könyvtár
  TTS** → Akkumulátor → **Korlátlan** (*Unrestricted*).

## A.7 A könyvek felmásolása

1. Csatlakoztasd a telefont USB-kábellel a PC-hez, és a telefonon válaszd a
   **„Fájlátvitel" (MTP)** módot.
2. Másold a könyvfájlokat tetszőleges mappába — ezek nyugodtan mehetnek
   SD-kártyára is.
3. Az appban mutasd meg a mappát (első indításkor rákérdez, később:
   **Beállítások → Könyvek gyökérmappája**), és indítsd el a beolvasást.

**Katalógust nem kell felmásolnod**: az app maga építi a telefonon lévő
könyvekből. A kész katalógus a `Download/KonyvtarTTS/sajat_katalogus.db`
fájlba kerül — szándékosan látható helyre, hogy túlélje az app
újratelepítését, és PC-n is megnyitható legyen.

> **A könyvek mehetnek kártyára**, a katalógus viszont a belső tárolón jó
> helyen: az SQLite sok apró, véletlenszerű olvasást végez, ami
> memóriakártyán érezhetően lassabb. A könyvfájlokat az app egyszer olvassa
> végig, utána a kinyert szöveg gyorsítótárból jön.

---

# B) Bluetooth és fülhallgató

## B.1 Párosítás

Semmi különleges nem kell: párosítsd a fülhallgatót a szokásos módon
(Beállítások → Bluetooth), és az app hangja automatikusan oda megy, mint
minden más médiahang.

- **Kodek nem számít.** A felolvasás beszéd, nem zene: az SBC ugyanolyan jó
  hozzá, mint az aptX vagy az LDAC. Késleltetéssel sem kell foglalkozni.
- **Hangerő:** a telefon *média* hangereje szabályozza.

## B.2 A gombok

Az app a hivatalos Android médiavezérlést (MediaSession) használja, így a
Bluetooth-os és a vezetékes fülhallgatók gombjai is működnek:

| Nyomás | Hatás |
|---|---|
| **1×** | Start / Stop (lejátszás ⇄ szünet) |
| **2×** | ~5 másodperc vissza |
| **3×** | szintén visszaugrás |

Többgombos fülhallgatón a **közép/lejátszás gomb** az, amelyik számít; a
hangerőgombok a szokásos módon működnek.

> **Miért „~5 másodperc"?** A felolvasásnak nincs valódi idővonala, mint egy
> zeneszámnak. Az app a beállított beszédsebességből számolja át, mennyi
> szöveg az 5 másodperc, és a legközelebbi **mondat elejére** ugrik vissza —
> így sosem a mondat közepén folytatja.

## B.3 Ha nem reagál a gomb

Sorrendben ezeket érdemes megnézni:

1. **Fut-e még a felolvasás?** A gombvezérlés addig él, amíg az értesítés
   látszik (lejátszás vagy szünet). Ha az ⏹ **Stop** gombbal teljesen
   leállítottad, a fülhallgató gombja szándékosan nem indítja újra — hogy ne
   szólaljon meg váratlanul a zsebedben. Indítsd az appból.
2. **Másik médialejátszó kapta el.** Az Android annak az appnak küldi a
   gombnyomást, amelyik **utoljára játszott** hangot. Ha közben megnyílt egy
   videó vagy zenelejátszó, az „viszi" a gombot. Megoldás: indítsd újra a
   felolvasást az appból (ezzel visszakerül hozzánk a vezérlés), vagy zárd be
   a másik lejátszót.
3. **A fülhallgató saját alkalmazása átdefiniálta a gombot.** Sok fülesnél a
   dupla koppintás gyárilag a hangasszisztenst hívja vagy a következő számra
   ugrik. Ezt a gyártó appjában lehet átállítani:
   *Galaxy Wearable* (Samsung), *Sony | Headphones Connect*, *JBL Headphones*,
   *Bose Music*, *Soundcore* stb. → Gombok/Érintésvezérlés → állítsd
   „Lejátszás/Szünet" és „Következő szám" funkcióra.
4. **Multipoint (két eszközhöz csatlakozik egyszerre).** Ha a fülhallgató a
   laptophoz is kapcsolódik, a gombnyomás oda mehet. Bontsd a másik
   kapcsolatot, vagy indíts lejátszást a telefonon, hogy az legyen az aktív.
5. **Régi vagy nagyon olcsó fülhallgató.** Néhány típus egyáltalán nem küld
   médiagombot, csak hívást fogad. Ilyet sajnos nem tud kezelni egy app sem.

## B.4 Mi látszik a fülhallgatón, órán, autórádión

Az app közli a **könyv címét és szerzőjét** médiaadatként, így megjelenik az
autórádió kijelzőjén, az okosórán és az értesítési sávban is. A vezérlőgombok
(előző/lejátszás/következő) ugyanezeken a felületeken is működnek.

## B.5 Automatikus viselkedés

Az app tisztességesen viselkedik a többi hangforrással:

| Esemény | Mi történik |
|---|---|
| **Hívás érkezik** | A felolvasás szünetel, és a pozíció mentődik. |
| **Másik app hangot ad** (videó, navigáció) | Szünetel, ha az tartósan elveszi a hangfókuszt. |
| **Kihúzod a fülhallgatót / lecsatlakozik a Bluetooth** | Azonnal szünetel — nem kezd hangosan beszélni a telefonhangszórón. |
| **Képernyő kikapcsol** | Zavartalanul szól tovább (előtér-szolgáltatás + wake lock). |

A hívás vagy zavaró hang után a felolvasás **nem indul automatikusan** —
nyomj Play-t (a fülhallgatón is elég).

## B.6 Praktikus tippek

- **Az első mondat elvész?** Egyes fülhallgatók pár tized másodperc alatt
  ébrednek fel, és lenyelik a szöveg elejét. Ilyenkor nyomd meg a **⏮ előző
  mondat** gombot (vagy dupla nyomás a fülesen), és újra elmondja.
- **Vezetékes fülhallgató:** a 3,5 mm-es jack és az USB-C adapteres fülesek
  gombja ugyanígy működik. Ha az adapternek nincs gombja, az appból tudsz
  vezérelni.
- **Autóban:** párosítás után a felolvasás médiaként szól, a kormányon lévő
  gombok is működnek (ugyanaz a lejátszás/következő logika).

---

# C) Fejlesztéshez — fordítás forrásból

Ez a rész csak akkor kell, ha magad akarod lefordítani az appot.

## C.1 Amit telepíteni kell

| Eszköz | Verzió | Megjegyzés |
|---|---|---|
| **JDK** | 17 vagy újabb | Az Android Studio hoz magával egyet (JBR), külön nem kell telepíteni. |
| **Android Studio** | friss (Ladybug vagy újabb) | Vagy csak a *command line tools* + SDK, ha parancssorból dolgozol. |
| **Android SDK Platform 35** | API 35 | Android Studio → SDK Manager. |
| **Android SDK Build-Tools, Platform-Tools** | legfrissebb | Ugyanott. |
| **Gradle** | 8.13 | **Nem kell telepíteni** — a `gradlew` letölti magának az első futáskor. |

Az első fordításhoz **internetkapcsolat kell** (a Gradle és a könyvtárak
letöltéséhez, kb. 1 GB); utána offline is megy.

## C.2 A projekt könyvtári függőségei

Ezeket a Gradle automatikusan letölti, nem kell velük foglalkozni — csak
tájékoztatásul, hogy mi mire való:

| Függőség | Verzió | Mire kell |
|---|---|---|
| Android Gradle Plugin | 8.11.1 | a fordítás maga |
| Kotlin | 2.2.0 | a nyelv |
| Compose fordító plugin | 2.2.0 | a Compose UI fordítása |
| Compose BOM | 2025.01.00 | a Compose könyvtárak összehangolt verziói |
| compose ui, foundation, material3 | BOM-ból | a felület |
| material-icons-extended | BOM-ból | az ikonok |
| androidx.core:core-ktx | 1.15.0 | alap Android kiegészítők |
| androidx.activity:activity-compose | 1.9.3 | Activity + Compose összekötés |
| androidx.lifecycle:* | 2.8.7 | ViewModel, állapotkezelés |
| androidx.navigation:navigation-compose | 2.8.5 | képernyők közti navigáció |
| androidx.media:media | 1.7.0 | MediaSession — a fülhallgató-gombokhoz |
| kotlinx-coroutines-android | 1.9.0 | háttérszálak |
| com.tom-roush:pdfbox-android | 2.0.27.0 | PDF szövegréteg kinyerése |

Az EPUB, MOBI, FB2, RTF, DOCX, TXT és HTML olvasók **saját kódból** vannak,
külső könyvtár nélkül.

## C.3 A projekt beállítása

```bash
git clone https://github.com/sanyi1963bp/Vox-Libris.git
cd Vox-Libris
```

Hozz létre egy `local.properties` fájlt a projekt gyökerében, benne az SDK
elérési útjával (ezt a fájlt a `.gitignore` szándékosan kihagyja):

```properties
sdk.dir=C\:\\Users\\<felhasznalonev>\\AppData\\Local\\Android\\Sdk
```

Android Studióból ez automatikusan létrejön, amikor megnyitod a mappát.

## C.4 Fordítás

**Android Studióból:** nyisd meg a projekt mappáját, várd meg a Gradle
szinkront, majd Run ▶.

**Parancssorból (Windows PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

Az eredmény: `app\build\outputs\apk\release\app-release.apk` (~9 MB).

A release APK a **debug kulccsal** van aláírva, hogy azonnal telepíthető
legyen. Ha saját kulccsal szeretnéd aláírni (pl. Play Áruházba), írd át a
`signingConfig` sort az `app/build.gradle.kts` fájlban.

Csak a debug változathoz: `.\gradlew.bat assembleDebug`.

## C.5 Gyakori fordítási hibák

| Hibaüzenet | Megoldás |
|---|---|
| `SDK location not found` | Hiányzik vagy rossz a `local.properties` (lásd [C.3](#c3-a-projekt-beállítása)). |
| `Unsupported class file major version` / JDK-hiba | Régi JDK. Állítsd a `JAVA_HOME`-ot az Android Studio `jbr` mappájára. |
| `Could not resolve ...` | Nincs internet az első fordításnál, vagy proxy mögött vagy. |
| `Failed to install the following Android SDK packages` | Nyisd meg az SDK Managert, és telepítsd az API 35 platformot. |
