# Jinki

An Android music player. The UI is **100% native Jetpack Compose**; playback runs
in a **Media3 `MediaSessionService`** so audio survives backgrounding and drives
lock-screen / notification controls.

> **Status: feature-complete with platform optimizations.** Navigation, theming (bundled
> Fraunces/Inter), persistence, lyrics, the search backend, the Media3
> playback service, all four screens, playlists, queue management and
> Wi-Fi-only downloads are in. Platform optimizations for startup, battery, network,
> and multi-form-factor support added.

## Stack

| | |
|---|---|
| Kotlin | 1.9.24 |
| Jetpack Compose | Material 3 (BOM 2024.09.03) |
| Media3 | 1.3.1 (ExoPlayer + MediaSessionService) |
| Search backend | NewPipeExtractor v0.26.5 (JitPack) |
| Persistence | Room 2.6.1 (WAL mode) + DataStore |
| AGP / Gradle | 8.4.1 / 8.7 |
| compileSdk / targetSdk | 34 |
| minSdk | 24 (desugaring on, so `java.time` works) |
| JDK | 17 |

## Platform Optimizations

### Startup Performance
- **Baseline Profiles**: `BaselineProfileGenerator` tests generate profiles for startup and search flows
- **Profile Installer**: Installs compiled profiles on first run via `ProfileInstaller.writeProfile()`
- **R8 Full Mode**: Aggressive code shrinking and optimization (`android.enableR8.fullMode=true`)
- **Multi-dex**: Enabled for faster incremental builds
- **KSP Incremental**: Faster Room compilation

### App Size & Delivery
- **ABI Splits**: Separate APKs per architecture (arm64-v8a, armeabi-v7a, x86, x86_64)
- **Density Splits**: Per-density APKs (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- **Shrink Resources**: Removes unused resources in release
- **ProGuard Optimizations**: 5 passes, interface merging, overload aggressive
- **Native Libraries**: `libc++_shared.so` deduplication

### Runtime Performance
- **Room WAL Mode**: Write-Ahead Logging for better concurrent read/write
- **OkHttp Caching**: 50MB disk cache with connection pooling (10 connections, 5 min keep-alive)
- **Coroutines**: Supervisor scopes, proper cancellation handling
- **Compose Compiler Reports**: Disabled in release for smaller binary

### Battery & Network
- **WorkManager**: Expedited downloads with Wi-Fi constraint, exponential backoff
- **Network Security Config**: Pin trusted domains, no cleartext traffic
- **Foreground Service Types**: Proper mediaPlayback and dataSync declarations
- **Wake Lock**: Network wake mode for downloads, partial for playback

### Form Factor Support
- **Edge-to-Edge**: Transparent status/navigation bars with insets handling
- **Predictive Back**: Android 14+ back gesture animation
- **Window Insets**: Compose `WindowInsets` for proper padding across phones, tablets, foldables
- **Multi-window**: `resizeableActivity="true"` via configChanges

### Build Performance
- **Gradle Daemon**: 4GB heap, parallel execution, configuration cache
- **Kotlin Incremental**: Classpath-aware incremental compilation
- **Dex Options**: 4GB heap, pre-dex libraries, 4 parallel processes

## Architecture

```
com.jinki.app/
├── JinkiApplication.kt        Room database holder + ProfileInstaller
├── MainActivity.kt            single activity, edge-to-edge, predictive back, Compose host
├── data/
│   ├── Models.kt              Track, OfflineTrack, PlaybackState, ListenDay
│   ├── JinkaDatabase.kt       Room DAOs (WAL mode, custom executors)
│   ├── ListenStats.kt         day keys + listening-time tracker
│   ├── TrackRepository.kt     offline library + resume point over Room
│   ├── UserPrefs.kt           DataStore: repeat mode, user name
│   ├── lyrics/                LrcParser, LrcLibClient, LyricsRepository
│   ├── artwork/               ArtworkMatcher (SoundCloud image matching)
│   ├── download/              DownloadManager (WorkManager, progress, cancel, disk space)
│   └── source/                the search backend seam — see below
├── player/
│   ├── PlaybackService.kt     MediaSessionService + ExoPlayer
│   ├── PlayerConnection.kt    MediaController binding, PlayerUiState flow
│   ├── RepeatMode.kt          off/all/one, mapped to the player
│   └── SleepTimer.kt          coroutine sleep timer, service-hostable
└── ui/
    ├── JinkaApp.kt            Scaffold + mini-player + NavHost + WindowInsets
    ├── nowplaying/            NowPlayingScreen + ViewModel
    ├── search/                SearchScreen + ViewModel
    ├── library/               LibraryScreen + ViewModel
    ├── user/                  UserScreen + ViewModel
    ├── components/            SongRow, bottom-sheet bodies
    ├── waveform/              seeded decorative Waveform
    └── theme/                 Color, Type, Theme (ported from the CSS tokens)
```

## Architecture

```
com.jinki.app/
├── JinkiApplication.kt        Room database holder
├── MainActivity.kt            single activity, edge-to-edge, Compose host
├── data/
│   ├── Models.kt              Track, OfflineTrack, PlaybackState, ListenDay
│   ├── JinkaDatabase.kt       Room DAOs
│   ├── ListenStats.kt         day keys + listening-time tracker
│   ├── TrackRepository.kt     offline library + resume point over Room
│   ├── UserPrefs.kt           DataStore: repeat mode, user name
│   ├── lyrics/                LrcParser, LrcLibClient, LyricsRepository
│   ├── artwork/               ArtworkMatcher (SoundCloud image matching)
│   ├── download/              DownloadManager (progress, cancel, disk space)
│   └── source/                the search backend seam — see below
├── player/
│   ├── PlaybackService.kt     MediaSessionService + ExoPlayer
│   ├── PlayerConnection.kt    MediaController binding, PlayerUiState flow
│   ├── RepeatMode.kt          off/all/one, mapped to the player
│   └── SleepTimer.kt          coroutine sleep timer, service-hostable
└── ui/
    ├── JinkaApp.kt            Scaffold + mini-player + NavHost
    ├── nowplaying/            NowPlayingScreen + ViewModel
    ├── search/                SearchScreen + ViewModel
    ├── library/               LibraryScreen + ViewModel
    ├── user/                  UserScreen + ViewModel
    ├── components/            SongRow, bottom-sheet bodies
    ├── waveform/              seeded decorative Waveform
    └── theme/                 Color, Type, Theme (ported from the CSS tokens)
```

### Search backend

The app holds two interfaces, not a library:

| | |
|---|---|
| `SearchSource` | query -> `List<SearchHit>`, unresolved |
| `StreamResolver` | `SourceRef` -> `ResolvedStream`: audio URL + title + artist + artwork, all from one extraction |

`SourceRegistry` is the only place that knows which implementations exist, and
it keeps two lists for two different failure modes: search backends are tried in
order and the first to return hits wins, so a broken extractor can be covered by
a fallback; resolvers are routed by source, because a track found through one
backend has to be played by the one that can serve it.

`NewPipeBackend` is the current implementation — YouTube Music, YouTube and
SoundCloud. It is also the only file that touches NewPipeExtractor's API, so a
tag bump that renames a getter breaks there and nowhere else. yt-dlp goes in
as a second entry in both lists.

### Design tokens

`ui/theme/Color.kt` and `ui/theme/Type.kt` are a direct transcription of the
`:root` block from the original web stylesheet (`--bg`, `--space-*`, `--r-*`,
Fraunces/Inter), so the native UI keeps the original design's metrics.

## The original web app

This project began as a WebView app. That web tier is **preserved at
`_reference/web/`** (HTML/CSS/JS) as the source of truth for the port — the
artwork-matching heuristics, LRC parsing, waveform generator and lyrics
fallback logic all port from it nearly 1:1.

It is not part of the build. Delete it once the port is finished.

## Building

```bash
./gradlew assembleDebug
```

**`gradle/wrapper/gradle-wrapper.jar` is not in the repo** (it could not be
produced in the environment this was generated in). Generate it once:

```bash
gradle wrapper --gradle-version 8.7
```

Then always build with `./gradlew`, never bare `gradle`.

> If you are building on-device with AndroidIDE, note that AGP 8.x and JDK 17
> are a real change from the old AGP 7.4.2 / JDK 11 setup. The old versions
> were pinned deliberately for AndroidIDE's bundled toolchain; the upgrade buys
> you a current, supported toolchain at the cost of that compatibility. If
> AndroidIDE can't cope, `compileSdk`/`targetSdk`/Kotlin can be rolled back
> independently — see [TODO.md](TODO.md).

### Release build & signing

The release build requires a signing keystore. **Never commit the keystore or passwords.**

1. **Generate the keystore** (run once, keep the `.jks` file safe — back it up to a USB stick or password manager; losing it means you can never update the app):

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias jinki-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_KEYSTORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=Jinki, OU=Engineering, O=Jinki, L=City, ST=State, C=US"
```

2. Create `keystore.properties` in the project root (copy `keystore.properties.example` and fill in):

```properties
storeFile=../release.keystore
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=jinki-release
keyPassword=YOUR_KEY_PASSWORD
```

3. Build the release APK:

```bash
./gradlew assembleRelease
```

The output is a **single universal APK** at `app/build/outputs/apk/release/app-release.apk` — no ABI or density splits.

> **Never commit** `keystore.properties` or `release.keystore`. They are gitignored. Back up the `.jks` file and passwords to a safe place (password manager, USB stick). Losing the keystore means you can never update the app on the Play Store.

## Known gaps

- **Nothing has compiled.** Every tier above was written without a compiler
  or a device. The first `./gradlew assembleDebug` is the real test — see
  TODO.md for the prime suspects, per tier.
- **This box can't package an APK.** The SDK's `zipalign` is x86-64 and the
  machine is aarch64; `:app:packageDebug` will fail until a native zipalign
  (or qemu-user + binfmt) is available.
- **The backend has never run.** Extraction, stream URLs and downloads are
  untested against the live services.
