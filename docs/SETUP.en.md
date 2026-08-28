# Vox Libris — installation and dependencies

🇬🇧 English (this page) · 🇭🇺 [Magyar](TELEPITES.md) · ⬅ [Back to the main page](../README.en.md)

Everything you need: for running the app on a phone, for Bluetooth headsets,
and for building from source.

> **Note:** the app's user interface is in Hungarian. Where a setting or
> button matters, this guide gives the Hungarian text with the translation.

---

## Contents

**A) On the phone — to use the app**
1. [Requirements](#a1-requirements)
2. [Installing the APK](#a2-installing-the-apk)
3. [Permissions](#a3-permissions)
4. [TTS engine and voices](#a4-tts-engine-and-voices)
5. [Other languages](#a5-other-languages)
6. [Disabling battery optimisation](#a6-disabling-battery-optimisation)
7. [Copying the catalogue and the books](#a7-copying-the-catalogue-and-the-books)

**B) [Bluetooth and headsets](#b-bluetooth-and-headsets)**

**C) [Building from source](#c-building-from-source)**

---

# A) On the phone

## A.1 Requirements

| Requirement | Detail |
|---|---|
| **Android 11 (API 30) or newer** | The app uses modern storage handling and foreground services. |
| **A text-to-speech engine** | A separate app, not bundled. See [A.4](#a4-tts-engine-and-voices). |
| **~30 MB free space** | The app itself is ~9 MB; the rest is the extracted-text cache (a few hundred KB per book). |
| **Nothing else** | No internet, no account, no Google Play Services, no root required. |

> The app **requests no internet access**. The TTS engine is a separate
> application, which may have its own network needs — see
> [A.4](#a4-tts-engine-and-voices).

## A.2 Installing the APK

1. Download `vox-libris-<version>.apk` from the
   [Releases](https://github.com/sanyi1963bp/Vox-Libris/releases) page, or
   copy it over from a PC via USB.
2. Open it in the phone's file manager and tap it.
3. Android will ask whether you trust the source: allow "install unknown
   apps" for **the app you are installing from** (file manager or browser).
   This is a one-off step.
4. **To update**, install the new APK over the old one — your data (reading
   positions, bookmarks, scan cache) is preserved.

## A.3 Permissions

The app uses three permissions, all requestable on first launch:

| Permission | What for | Where |
|---|---|---|
| **All files access**<br>(`MANAGE_EXTERNAL_STORAGE`) | Required. Without it the app cannot see your books or the catalogue. | The button on the app's welcome screen takes you to system settings. |
| **Notifications** | Playback controls in the notification shade and on the lock screen. Needed from Android 13. | Popup on first launch. |
| **Battery: unrestricted** | So the system does not kill long narration sessions. Optional but strongly recommended. | See [A.6](#a6-disabling-battery-optimisation). |

The app requests **no** location, contacts, camera, microphone or internet
access.

## A.4 TTS engine and voices

Narration uses the **system TTS engine** — a separate application you
configure once, which then serves every app that speaks.

### Google Text-to-Speech (recommended)

It has the best Hungarian voice and is preinstalled on most phones.

1. **Install (if missing):** Play Store → search for
   *Speech Recognition & Synthesis* (formerly *Google Text-to-Speech*).
   Package name: `com.google.android.tts`.
2. **Make it the default:**
   - **On Samsung:** Settings → General management → **Text-to-speech**
   - **Other phones:** Settings → Accessibility → **Text-to-speech output**
   - Or simply: in the app, **Settings → "Rendszer TTS beállítások…"**
     (system TTS settings), which jumps straight there.
3. Set **Preferred engine** to Google.
4. **Language:** pick yours. If there is a download icon next to it, tap it —
   that fetches the offline voice data (a few tens of MB).
5. **Test** it with the "Play"/"Listen to an example" button.
6. **Speed and pitch:** do **not** set them here — the app has its own
   sliders (the 🎚 tuning button in the reader), which override the system
   values.

> **Offline use:** once the voice is downloaded, narration works without an
> internet connection. Some Google voices (the "natural"/neural ones) require
> the network — if you listen on the metro or in traffic, stick to a
> downloadable offline voice.

### Alternative engines

| Engine | Notes |
|---|---|
| **Samsung TTS** | Preinstalled on Samsung phones, but not every device or region offers every language. If yours is missing from the list, switch to the Google engine. |
| **eSpeak NG** | Free, open source, fully offline, tiny. It sounds robotic but covers a very wide set of languages — a good fallback when nothing else is available. |
| **Commercial engines** | Paid engines with more natural voices exist; they appear in the same system setting, and the app automatically uses whichever is the default. |

The app is **not tied to any engine**: it uses whatever the system default
is. After switching engines, just restart the app.

## A.5 Other languages

The app **looks for a Hungarian voice first**, and only falls back to the
phone's system language if Hungarian is unavailable.

So for a book in another language:

1. Download the language in the TTS engine's settings (same place as any
   other: select language → download).
2. Change the **system TTS language** to it (in the engine's settings), or
   remove the Hungarian voice.
3. Restart the app.

> **An honest limitation:** there is currently no per-book language selector.
> If a Hungarian voice is installed, it is always used. Reading an English
> book with a Hungarian voice is unpleasant, so the manual switch above is
> needed. If this bothers you, open an issue — per-book language selection
> can be added.

## A.6 Disabling battery optimisation

During long narration the system's power saving may kill the service. It is
worth exempting the app:

- **Samsung:** Settings → Battery and device care → Battery →
  **Background usage limits** → *Never sleeping apps* → add **Könyvtár TTS**.
- **Any Android:** Settings → Apps → **Könyvtár TTS** → Battery →
  **Unrestricted**.

## A.7 Copying the catalogue and the books

1. Connect the phone to the PC over USB and choose **File transfer (MTP)** on
   the phone.
2. Copy the catalogue `.db` into the root of **internal storage** or into
   `Download`. **The `.db` only** — not the `-wal` and `-shm` files! (If the
   PC-side program was running, close it first so the `.db` is complete.)
3. Copy your book files anywhere — these are perfectly fine on an SD card.
4. In the app: **Settings → "Adatbázisfájl kiválasztása…"** (select database
   file) if it is not in one of the usual locations.

> **Why internal storage for the `.db`?** SQLite performs many small random
> reads, which are noticeably slower on a memory card. Book files are fine on
> a card: the app reads each one once, and afterwards the extracted text
> comes from the cache.

---

# B) Bluetooth and headsets

## B.1 Pairing

Nothing special is required: pair the headset the usual way (Settings →
Bluetooth) and the app's audio goes there like any other media sound.

- **The codec does not matter.** Narration is speech, not music: SBC is just
  as good as aptX or LDAC. Latency is irrelevant too.
- **Volume** is controlled by the phone's *media* volume.

## B.2 The buttons

The app uses the official Android media control layer (MediaSession), so both
Bluetooth and wired headset buttons work:

| Press | Effect |
|---|---|
| **1×** | play / pause |
| **2×** | rewind ~5 seconds |
| **3×** | also rewinds |

On multi-button headsets it is the **centre/play button** that matters;
volume buttons work as usual.

> **Why "~5 seconds"?** Narration has no real timeline like a music track.
> The app converts 5 seconds into an amount of text using your configured
> speech rate, and jumps back to the nearest **sentence start** — so it never
> resumes mid-sentence.

## B.3 If the button does nothing

Check these in order:

1. **Is narration still loaded?** Button control lives as long as the
   notification is visible (playing or paused). If you fully stopped it with
   ⏹, the headset button deliberately will not restart it — so it cannot
   start talking in your pocket. Start it from the app.
2. **Another media player grabbed it.** Android sends the button press to
   the app that **played most recently**. If a video or music player has
   started since, it "owns" the button. Fix: start narration again from the
   app (which hands control back), or close the other player.
3. **The headset's companion app remapped the button.** On many headsets a
   double tap invokes the voice assistant or skips tracks by default. Change
   it in the manufacturer's app: *Galaxy Wearable* (Samsung),
   *Sony | Headphones Connect*, *JBL Headphones*, *Bose Music*, *Soundcore*
   etc. → Touch controls → set to "Play/Pause" and "Next track".
4. **Multipoint (connected to two devices).** If the headset is also paired
   with your laptop, presses may go there. Disconnect the other device, or
   start playback on the phone so it becomes the active source.
5. **Old or very cheap headsets.** Some models send no media button at all,
   only call control. No app can work around that.

## B.4 What shows on headsets, watches and car stereos

The app publishes the **book title and author** as media metadata, so it
appears on car stereo displays, smart watches and in the notification shade.
The transport buttons (previous/play/next) work on those surfaces too.

## B.5 Automatic behaviour

The app plays nicely with other audio sources:

| Event | What happens |
|---|---|
| **Incoming call** | Narration pauses and the position is saved. |
| **Another app plays audio** (video, navigation) | Pauses if that app takes audio focus for good. |
| **Headphones unplugged / Bluetooth disconnects** | Pauses immediately — it will not start blaring from the phone speaker. |
| **Screen turns off** | Keeps playing (foreground service + wake lock). |

After a call or interruption narration does **not** resume automatically —
press play (the headset button is enough).

## B.6 Practical tips

- **First sentence cut off?** Some headsets take a fraction of a second to
  wake up and swallow the start. Press the **⏮ previous sentence** button (or
  double-press on the headset) and it will be repeated.
- **Wired headsets:** 3.5 mm jack and USB-C adapter headsets work the same
  way. If your adapter has no button, control it from the app.
- **In the car:** after pairing, narration plays as media, and the steering
  wheel buttons work with the same play/next logic.

---

# C) Building from source

Only needed if you want to compile the app yourself.

## C.1 What to install

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 17 or newer | Android Studio bundles one (JBR); no separate install needed. |
| **Android Studio** | recent (Ladybug or newer) | Or just the *command line tools* + SDK for a headless build. |
| **Android SDK Platform 35** | API 35 | Android Studio → SDK Manager. |
| **Android SDK Build-Tools, Platform-Tools** | latest | Same place. |
| **Gradle** | 8.13 | **No install needed** — `gradlew` downloads it on first run. |

The first build needs an **internet connection** (roughly 1 GB of Gradle and
libraries); after that it works offline.

## C.2 Library dependencies

Gradle fetches these automatically — listed here only so you know what each
one is for:

| Dependency | Version | Purpose |
|---|---|---|
| Android Gradle Plugin | 8.11.1 | the build itself |
| Kotlin | 2.2.0 | the language |
| Compose compiler plugin | 2.2.0 | compiling Compose UI |
| Compose BOM | 2025.01.00 | aligned Compose library versions |
| compose ui, foundation, material3 | from BOM | the user interface |
| material-icons-extended | from BOM | the icons |
| androidx.core:core-ktx | 1.15.0 | Android core extensions |
| androidx.activity:activity-compose | 1.9.3 | Activity + Compose bridge |
| androidx.lifecycle:* | 2.8.7 | ViewModel, state handling |
| androidx.navigation:navigation-compose | 2.8.5 | screen navigation |
| androidx.media:media | 1.7.0 | MediaSession — the headset buttons |
| kotlinx-coroutines-android | 1.9.0 | background work |
| com.tom-roush:pdfbox-android | 2.0.27.0 | PDF text layer extraction |

The EPUB, MOBI, FB2, RTF, DOCX, TXT and HTML parsers are **hand-written**,
with no external library.

## C.3 Project setup

```bash
git clone https://github.com/sanyi1963bp/Vox-Libris.git
cd Vox-Libris
```

Create a `local.properties` file in the project root pointing at your SDK
(this file is deliberately excluded by `.gitignore`):

```properties
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
```

Android Studio creates it automatically when you open the folder.

## C.4 Building

**From Android Studio:** open the project folder, wait for the Gradle sync,
then hit Run ▶.

**From the command line (Windows PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

Result: `app\build\outputs\apk\release\app-release.apk` (~9 MB).

The release APK is signed with the **debug key** so it installs immediately.
To sign it with your own key (e.g. for the Play Store), edit the
`signingConfig` line in `app/build.gradle.kts`.

Debug build only: `.\gradlew.bat assembleDebug`.

## C.5 Common build errors

| Error | Fix |
|---|---|
| `SDK location not found` | `local.properties` is missing or wrong (see [C.3](#c3-project-setup)). |
| `Unsupported class file major version` / JDK errors | Old JDK. Point `JAVA_HOME` at Android Studio's `jbr` folder. |
| `Could not resolve ...` | No internet on the first build, or you are behind a proxy. |
| `Failed to install the following Android SDK packages` | Open the SDK Manager and install the API 35 platform. |
