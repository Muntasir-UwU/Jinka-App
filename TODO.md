# TODO

## Execution Plan

### STAGE 1 - Install and signing (P0 #1, #2) — ✅ DONE
- [x] **Universal APK** — `universalApk true` in build.gradle, no ABI/density splits
- [x] **Release signingConfig** — reads from `keystore.properties` / env vars, never hardcoded
- [x] `keystore.properties` and `*.jks` in `.gitignore`
- [x] `keytool` command documented in README
- [x] Release build type uses `signingConfig signingConfigs.release`
- [x] `keystore.properties.example` created as template

### STAGE 2 - Notifications and downloads (P0 #3, P1 #8)
- [ ] Request `POST_NOTIFICATIONS` at runtime on Android 13+, at a sensible moment before the first download. Handle denial gracefully.
- [ ] Make sure `DownloadWorker` still works if notifications are denied: no crash, a clear message to the user, and a fallback or retry path.
- [ ] Add the foreground service type and permissions the expedited work needs on newer Android versions (including `FOREGROUND_SERVICE_SHORT_SERVICE` or the correct type for this worker). Verify the manifest merges correctly.

### STAGE 3 - Code fixes (P0 #6, P1 #9)
- [ ] Remove the bogus `window.setOnBackInvokedDispatcher` call in `MainActivity.kt` and implement predictive back properly: set `android:enableOnBackInvokedCallback` in the manifest and use the AndroidX `OnBackPressedDispatcher`/`BackHandler`.
- [ ] Remove unused `androidx.startup` imports in `JinkiApplication`.

### STAGE 4 - Toolchain update (P0 #4, P1 #7)
- [ ] Update to the latest stable: Android Gradle Plugin, Gradle wrapper, Kotlin (and the Compose compiler plugin if needed), `compileSdk`, and `targetSdk`. Keep `minSdk` 24.
- [ ] Update AndroidX, Compose BOM, WorkManager, Media3, DataStore, Room, Coil and all other dependencies to current stable versions.
- [ ] Fix every deprecation and compile error this causes, and apply the Android 15, 16 and 17 behavior changes that affect this app (check the official docs).
- [ ] After building, verify 16 KB page alignment of all `.so` files in the final APK (`zipalign -c -P 16` and a check of the ELF alignment). Replace any dependency that still ships unaligned libraries.

## Done — base scaffold
- [x] Compose app shell: `MainActivity` + `JinkaTheme` + 4-destination `NavHost`
- [x] Design tokens ported from the original `:root` CSS block
- [x] Room database (offline tracks, playback state, listen history)
- [x] Launcher icon (adaptive + legacy density buckets)
- [x] `strings.xml` — all user-facing text extracted from the web tier
- [x] ProGuard rules for Media3 / Room
- [x] Web tier removed from the build, preserved at `_reference/web/`
- [x] Listen-time day keys + tracker
- [x] `LrcParser` — from `app.js parseLyric` + `syncLyric`
      (carries over the `syncLyric` index fix)
- [x] `LyricsRepository` — lrclib client, 4-tier fallback
      (`get` → `search` → broad → romanized) + language-acceptance heuristic

## Done — search backend
- [x] **Backend decided:** NewPipeExtractor (JitPack `v0.24.0`). YouTube Music +
      YouTube + SoundCloud. yt-dlp stays available as a later fallback — it wins
      on update speed and loses on APK size and per-call latency, so it is a
      second backend rather than the first.
- [x] `SearchSource` / `StreamResolver` seam, so the library is swappable
- [x] `SourceRegistry`: search tried in order (first non-empty wins), resolvers
      routed by source
- [x] `NewPipeBackend` — songs-filtered YouTube Music search + SoundCloud,
      merged and deduped, one dead service doesn't fail the other
- [x] `resolve` picks the highest-bitrate audio rendition; extraction failures
      translated into messages (age-gated / geo-blocked / live / sign-in)
- [x] Ported `normalizeSearchThumbnails` — mqdefault / maxresdefault / hqdefault
- [ ] **Verify against a real build.** The extractor API surface was written
      without a compiler or a device. If the tag's API differs, the fixes belong
      in `NewPipeBackend` alone.

## Done — middle tier ports
- [x] `Waveform` — `mulberry32` / `generateWaveformHeights` ported exactly
      (FNV-1a seed, 3 sine waves, 75/25 noise mix), drawn on `Canvas` with
      played/unplayed colours from the CSS tokens. Bar count derives from
      measured width, so no resize listener is needed.
- [x] `ArtworkMatcher` — `normalizeArtworkText`, `artworkTokenScore`,
      `findMatchedSoundCloudArtwork` with the same weights and thresholds
      (0.5/0.35/0.15, total ≥ 0.68). Takes the last search results as a
      parameter instead of re-reading sessionStorage; the 120×90 probe gate
      moves to the Coil layer.
- [x] `TrackRepository` over Room — observe/save/remove the offline library,
      resume-point save/observe. `remove` deletes the audio file too, until
      the download manager owns files.
- [x] Shared components: `SongRow` (thumb fallback chain, status slot),
      bottom-sheet bodies for lyrics / timer / message. Gesture
      disambiguation (280 ms tap, triple-tap remove) stays in the screens.
- [x] Sleep timer as a coroutine (`player/SleepTimer`, service-hostable).
      No persistence — a dead process has no audio to stop, so the deadline
      lives and dies with the service scope. Clears before firing, exactly
      once, like the web watchdog.

## Middle — ports with a known reference in `_reference/web/js/`
(none left — all ported)

## Done — first successful build (`assembleDebug`, 24 MB APK)
- [x] `gradle-wrapper.jar` generated; wrapper pinned at Gradle 8.7, JDK 17
- [x] `local.properties` (`sdk.dir`), AGP auto-installed android-34 + build-tools 34
- [x] Fixed `isMinifyEnabled`/`isShrinkResources` (AGP 4.x names, rejected by AGP 8)
- [x] Fixed `--` inside XML comments (`themes.xml`, `ic_launcher_background.xml`)
- [x] Root Gradle home mirrors the `aapt2FromMavenOverride` (SDK aapt2 is
      x86-64; Debian's native aapt2 does the linking)
- [x] ARM64 `zipalign` + `adb` installed from Debian; SDK binary swapped
- [x] NewPipeBackend rewritten against the real v0.24.0 API (verified with
      `javap`: `ServiceList`, `SearchInfo.getInfo`, `StreamInfo.getInfo`,
      custom OkHttp `Downloader`, new exception names)
- [x] Bumped to v0.26.5 (latest; v0.24.0 is ~2 years old and YouTube's player
      responses have moved since). Every touched API re-verified against the
      v0.26.5 jar with `javap` — unchanged. New deps it pulls in
      (protobuf-javalite, rhino) confirm the parser moved with YouTube.
- [x] Downloader hardened: default Chrome User-Agent when the extractor sets
      none, post-redirect URL as `latestUrl`.
- [x] Download reliability: 1 MB ranged chunks (plain whole-file GETs get
      throttled/rejected), progressive-only rendition filter, manifest
      content-type guard, format-suffix filenames, one shared User-Agent,
      catch-all failure reporting, SoundCloud stored-track re-resolve.
- [x] Download hardening round 2: chunk completion as return value (break
      can't cross lambdas), 416-as-done, atomic call reference +
      cooperative cancellation, incomplete-size rejection, atomic state
      updates, 1%-gated progress.
- [x] Downloads run under WorkManager: expedited work with a foreground
      notification, backoff retry for IO failures, Wi-Fi-only as a network
      constraint (waits instead of failing). Same manager surface, so no
      screen changed. First device run must confirm the notification shows
      and expedited quota behaves on API 34.
- [ ] **Later, if needed:** `&range=` URL parameter instead of the `Range`
      header (only if 403s persist — NewPipe does this for some URLs), and
      moving downloads to WorkManager/a foreground service so they survive
      backgrounding.
- [x] Fixed the 10 kotlinc errors (`SearchHit.toSourceRef`, `coroutineContext`
      import, nullable audio URL, restored `fallbackArtwork`, `isNullOrBlank`,
      `combinedClickable` opt-in) + the `UnstableApi` flag warning
- [ ] **Install + runtime test.** Needs a device: `adb install -r app-debug.apk`,
      then one search, one resolve, one download, watching `logcat`. The
      extractor has never touched the live services.

## Done — hard tier (Media3 + screens)
- [x] `PlaybackService` — Media3 `MediaSessionService`, default notification
      provider, audio focus + noisy handling, wake lock, session callbacks.
      Repeat is enforced by the player itself (no `ended` handler — the web
      version's auto-advance stalled when backgrounded). Attaches the sleep
      timer's pause, tracks listen time, persists the resume point.
- [x] Service registered in `AndroidManifest.xml`
      (`foregroundServiceType="mediaPlayback"`)
- [x] `PlayerConnection` — binds `MediaController`, exposes
      `StateFlow<PlayerUiState>` (track, position ticker, repeat, errors).
      Same-track reload guard, 3-second prev-restart rule, repeat cycling —
      all ported from `JinkaPlayer`.
- [x] `NowPlayingScreen` + ViewModel (provided monochrome + amber design:
      top bar, artwork slot, title/like row, seekable waveform, transport
      with shuffle, Lyrics/Queue pills). A `NowPlayingRoute` adapter maps the
      player backend onto it: seeded waveform bars scaled to 0–1, artwork
      fallback chain, lyrics + queue sheets, like/repeat/shuffle state.
- [x] `SearchScreen` + ViewModel (450 ms debounce, `collectLatest` stale
      guard, tap-preview / double-tap-download with the 280 ms window,
      saved badges, download status rows, mini-player via the shared shell)
- [x] `LibraryScreen` + `DownloadManager` (Room-backed list, tap toggles,
      triple-tap removes with the 600 ms window; downloads resolve → stream
      → temp-file → rename → save, with progress, cancel, retry-by-retry and
      a disk-space pre-check)
- [x] `UserScreen` + ViewModel (greeting quote with the web thresholds,
      ticking today-listened clock, timer card + sheet on the shared timer,
      poke counter, local-only messages)
- [x] Replay mode persistence (`off` / `all` / `one`) in DataStore, applied
      to the player on service start
- [x] Playback reliability round (the "gets stuck / play button dead" report):
      custom HTTP data source with the shared Chrome user agent (the stock
      `ExoPlayerLib` agent is what YouTube 403s mid-track), a load-error policy
      and a roomier `LoadControl` for transient drops, a retry-then-skip ladder
      in `onPlayerError` (re-prepare is what an idle player needs before `play`
      works again), pending-command replay + queue adoption in
      `PlayerConnection` so cold-start taps and an already-playing service are
      handled, the whole `Track` in the media item's extras (no side table, no
      lost `pageUrl`), buffering + error state surfaced in the UI, new strings
      in `strings.xml`, and the `runBlocking` flush bounded.
- [x] Highest-quality artwork round: `resolve` and both search mappers take the
      largest image (the extractor lists smallest-first, so `firstOrNull` was
      the worst one), the YouTube ladder gains the missing `sddefault`
      (`maxres → sd → hq → mq`, since `maxres` 404s for old uploads), and a
      resolved `Track` gets the same ladder a search row has instead of one
      URL and a blank box.
- [x] Playback bugs round: expired-URL recovery (re-resolve once per URL, then
      advance or stop — no infinite loop), service queue rebuilt from the
      timeline after remove/move, resume point actually restored on launch
      (paused, no autoplay), pre-connect taps queued and replayed, player
      errors surfaced via snackbar with auto-clear, `canGoNext` honest under
      repeat-all, and a Loading status on resolving playlist rows.
- [ ] **Verify against a real build.** The Media3, DataStore and Coil API
      surface was written without a compiler. Prime suspects: Guava
      `Futures` availability for the session callback, `setWakeMode` /
      `setHandleAudioBecomingNoisy` builder names, `rememberModalBottomSheetState`
      params, and the service lifecycle on first bind.

## Blocked — needs a decision
- [ ] **Rolled-back toolchain?** AGP 8 / JDK 17 vs the previous AGP 7.4.2 /
      JDK 11 that was pinned for AndroidIDE. If AndroidIDE can't build it,
      say so and the versions get walked back.
- [ ] **ToS.** NewPipeExtractor is fine to depend on and fine to keep
      updatable, but extracting YouTube streams is not something YouTube's
      terms sanction. Not a code question — worth deciding before the backend
      ships to anyone but yourself.

## Done — extra hard (Ideas backlog)
- [x] Playlists — `playlists` + `playlist_entries` tables (migration v2→v3),
      `PlaylistRepository` (create/rename/delete, deduped add, remove),
      "save to playlist" sheet on search/library long-press, Library tabs
      (Songs/Playlists) with detail view. Entries snapshot display fields;
      stream-only entries resolve at tap time, saved ones queue from disk.
- [x] Queue management — queue mirror flow on `PlayerConnection`, jump /
      remove / move on both sides, queue sheet in Now Playing. Previews
      *append* via `enqueueAndPlay` (re-taps jump instead of duplicating),
      so the timeline always holds a next item for ExoPlayer to pre-buffer
      and transitions stay gapless; full replaces stay for library/playlist
      sets.
- [x] Gapless — ExoPlayer's default, nothing disables it. True crossfade is
      deliberately out (needs a second player or custom `AudioProcessor`).
- [x] Download over Wi-Fi only — DataStore toggle on the User screen,
      fail-fast gate in `DownloadManager` with its own message.
- [x] Bundle fonts — Fraunces + Inter variable TTFs in `res/font`, wired in
      `Type.kt` via `wght` variations (correct on API 26+, Regular fallback
      on 24/25). OFL-licensed.
- [ ] Custom launcher icon art + per-density PNGs — need art tooling that
      isn't here. The adaptive icon itself is done and well-formed
      (background + foreground + monochrome vectors); only the legacy
      pre-Oreo PNGs remain system-scaled.
