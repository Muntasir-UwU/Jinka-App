package com.jinki.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val AVATAR_DIR = "avatars"
private const val AVATAR_MAX_PX = 512
private const val AVATAR_JPEG_QUALITY = 85
private const val AVATAR_THUMB_PX = 128
private const val AVATAR_THUMB_JPEG_QUALITY = 80
private const val DECODE_LONG_SIDE_PX = 1024

/**
 * Copies a picked image into app-private storage, downscaled to fit within
 * 512 px and compressed as JPEG so it stays small.
 *
 * Plain language: the gallery only lends us a temporary link to the photo,
 * which can stop working later. This saves our own small copy under
 * filesDir/avatars/ and returns its path. Throws IOException on failure
 * so the caller can keep the old picture.
 */
suspend fun storeAvatar(context: Context, source: Uri, uid: String): String =
    withContext(Dispatchers.IO) {
        // Null/empty uid falls back to a fixed name instead of failing.
        val safeUid = uid.ifBlank { "local" }
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "local" }
        val dir = File(context.filesDir, AVATAR_DIR).apply { mkdirs() }
        val dst = File(dir, "avatar_${safeUid}.jpg")
        val tmp = File(dir, "avatar_${safeUid}.tmp")
        val resolver = context.contentResolver

        // Always open a fresh stream: each decode/EXIF pass consumes it.
        fun openFresh() =
            resolver.openInputStream(source)
                ?: throw IOException("Couldn't open the picked image (null stream).")

        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            // Pass 1: bounds only, no pixels allocated.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openFresh().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Couldn't read the picked image.")
            }

            // Power-of-2 sample so the longer side is at most ~1024 px.
            var sample = 1
            val longerSide = max(bounds.outWidth, bounds.outHeight)
            while (longerSide / sample > DECODE_LONG_SIDE_PX) {
                sample *= 2
            }

            // Pass 2: real decode, downsampled.
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = openFresh().use { BitmapFactory.decodeStream(it, null, opts) }

            // Fallback for formats BitmapFactory returns null for (e.g. HEIC).
            if (decoded == null && Build.VERSION.SDK_INT >= 28) {
                decoded = try {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(resolver, source)
                    ) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                    }
                } catch (_: Exception) {
                    null
                }
            }
            val src = decoded ?: throw IOException("Couldn't decode the picked image.")

            // EXIF orientation from a fresh stream; ignore failures (assume 0).
            val matrix = Matrix()
            try {
                openFresh().use { ins ->
                    val orientation = ExifInterface(ins).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            matrix.postRotate(90f)
                            matrix.postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            matrix.postRotate(270f)
                            matrix.postScale(-1f, 1f)
                        }
                        else -> Unit
                    }
                }
            } catch (_: Exception) {
                matrix.reset()
            }
            oriented = if (matrix.isIdentity) {
                src
            } else {
                try {
                    Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
                } catch (_: OutOfMemoryError) {
                    throw OutOfMemoryError("storeAvatar ran out of memory rotating the image")
                }
            }

            // Scale so the longer side is 512 px (never upscale).
            val w = oriented.width
            val h = oriented.height
            if (w <= 0 || h <= 0) throw IOException("Couldn't read the picked image.")
            val scale = if (max(w, h) > AVATAR_MAX_PX) {
                AVATAR_MAX_PX / max(w, h).toFloat()
            } else {
                1f
            }
            scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    oriented,
                    max(1, (w * scale).roundToInt()),
                    max(1, (h * scale).roundToInt()),
                    true
                )
            } else {
                oriented
            }

            // mkdirs first, then compress to temp.
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                throw IOException("Couldn't create the avatars directory.")
            }
            FileOutputStream(tmp).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, out)) {
                    throw IOException("Couldn't save the picked image.")
                }
            }
            // Rename over the final name. renameTo fails when dst exists on
            // many devices, so delete first, then copy as a last resort.
            if (!tmp.renameTo(dst)) {
                dst.delete()
                if (!tmp.renameTo(dst)) {
                    try {
                        tmp.copyTo(dst, overwrite = true)
                    } catch (e: Exception) {
                        throw IOException("Couldn't save the picked image.", e)
                    }
                    if (!dst.exists() || dst.length() == 0L) {
                        throw IOException("Couldn't save the picked image.")
                    }
                }
            }
            dst.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            // TEMP DEBUG AID (log) + real fix: OOM is an Error, not an
            // Exception, so without this the app would crash instead of
            // showing the error toast and keeping the old picture.
            Log.e("JinkiAvatar", "storeAvatar ran out of memory decoding the image", e)
            throw IOException("Photo is too large to process.", e)
        } catch (e: Exception) {
            // TEMP DEBUG AID: log the real cause; the toast below names it too.
            Log.e("JinkiAvatar", "storeAvatar failed: ${e.javaClass.simpleName}: ${e.message}", e)
            if (e is IOException) throw e
            throw IOException("Couldn't save the picked image.", e)
        } finally {
            // Recycle every distinct intermediate (safe if same instance).
            val seen = HashSet<Bitmap>()
            for (b in listOf(decoded, oriented, scaled)) {
                if (b != null && seen.add(b) && !b.isRecycled) {
                    try {
                        b.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            try {
                tmp.delete()
            } catch (_: Exception) {
            }
        }
    }


/**
 * 128 px JPEG thumbnail of the saved avatar, base64-encoded (NO_WRAP) for
 * the users/{uid}/avatar field. Stays around ~10 KB, safely under the
 * 20000-character server rule. Throws IOException on failure.
 */
suspend fun encodeAvatarThumb(context: Context, path: String): String =
    withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) throw IOException("Avatar file is gone.")
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Couldn't read the avatar file.")
            }
            var sample = 1
            while (bounds.outWidth / sample > AVATAR_THUMB_PX * 2 ||
                bounds.outHeight / sample > AVATAR_THUMB_PX * 2
            ) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: throw IOException("Couldn't decode the avatar file.")
            try {
                val scale = minOf(
                    1f,
                    AVATAR_THUMB_PX / decoded.width.toFloat(),
                    AVATAR_THUMB_PX / decoded.height.toFloat()
                )
                val final = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    decoded
                }
                try {
                    val bytes = ByteArrayOutputStream().use { out ->
                        if (!final.compress(Bitmap.CompressFormat.JPEG, AVATAR_THUMB_JPEG_QUALITY, out)) {
                            throw IOException("Couldn't encode the avatar thumbnail.")
                        }
                        out.toByteArray()
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } finally {
                    if (final !== decoded) final.recycle()
                }
            } finally {
                decoded.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw IOException("Photo is too large to process.", e)
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException("Couldn't encode the avatar thumbnail.", e)
        }
    }
