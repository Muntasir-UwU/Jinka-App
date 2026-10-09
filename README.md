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

