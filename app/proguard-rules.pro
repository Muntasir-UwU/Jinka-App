# --- Media3 / ExoPlayer ---
# Player/decoder classes are resolved reflectively by the default loaders.
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
# The session service and notification provider are reached via the manifest
# and Media3 internals rather than direct calls from app code.
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# MediaSessionService is discovered by the platform via the manifest.
-keep class com.jinki.app.player.PlaybackService { *; }

# --- Room ---
# Entities and generated DAOs are accessed reflectively by Room's runtime.
-keep class com.jinki.app.data.** { *; }
-dontwarn androidx.room.paging.**

# --- Kotlin coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- NewPipeExtractor ---
# The published jar is ProGuard-processed with only its public API kept, so the
# app touches nothing internal by name. Keeping the whole package anyway costs
# a little size and buys readable stack traces plus immunity to the jar being
# minified differently on the next tag bump.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Runtime-needed extractor dependencies. Rhino runs the YouTube signature
# deciphering, protobuf-javalite encodes the player requests, jsoup parses
# HTML pages, and nanojson parses every extractor response — all reached at
# runtime, so they are kept, not just silenced, under R8 full mode.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

# Optional extractor dependencies: present for some services, absent for
# others, and never referenced from app code.
-dontwarn com.google.gson.**
-dontwarn org.apache.commons.**
-dontwarn fr.catiepagniez.javatmpfile.**
-dontwarn net.brouwer.rijks.mime.**
-dontwarn javax.annotation.**
-dontwarn com.google.common.**

# --- Coil (2.x = `coil` package; `coil3` is the Coil 3 artifact) ---
-dontwarn coil.**
-keep class coil.** { *; }

# --- DataStore ---
# Preferences are protobuf-backed; keep the runtime reachable in full mode.
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# --- Optimizations (kept light: this project builds on-device) ---
# Fewer passes and no aggressive flags: full R8 + 5 passes thrashes phone RAM.
# Remove unused code lightly
-optimizationpasses 2

# Optimize for size and speed
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Keep annotations for reflection
-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
