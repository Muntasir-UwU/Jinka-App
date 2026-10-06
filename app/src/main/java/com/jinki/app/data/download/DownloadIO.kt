package com.jinki.app.data.download

import android.content.Context
import android.net.Uri
import com.jinki.app.R
import com.jinki.app.data.Track
import com.jinki.app.data.source.EXTRACTION_USER_AGENT
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext


internal suspend fun fetchTrackToFile(
    context: Context,
    http: OkHttpClient,
    track: Track,
    suffix: String?,
    onProgress: suspend (bytesWritten: Long, totalBytes: Long) -> Unit
): File {
    val dir = File(context.filesDir, MUSIC_DIR).apply { mkdirs() }
    val tmp = File(dir, "${track.source}_${track.id}.tmp")
    val callerJob = coroutineContext[Job]

    var extension = ".m4a"
    var start = 0L
    var total = -1L
    try {
        FileOutputStream(tmp).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var lastPct = -1
            var firstChunk = true
            while (true) {
                val googleVideo = isGoogleVideo(track.streamUrl)
                
                val clen = if (googleVideo) {
                    Uri.parse(track.streamUrl).getQueryParameter("clen")?.toLongOrNull()
                } else null
                val chunkUrl = if (googleVideo) {
                    track.streamUrl + (if ('?' in track.streamUrl) "&" else "?") +
                        "range=$start-${start + CHUNK_SIZE - 1}"
                } else {
                    track.streamUrl
                }
                val builder = Request.Builder()
                    .url(chunkUrl)
                    .header("User-Agent", EXTRACTION_USER_AGENT)
                if (!googleVideo) {
                    builder.header("Range", "bytes=$start-${start + CHUNK_SIZE - 1}")
                }
                val call = http.newCall(builder.build())
                val hook = callerJob?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) call.cancel()
                }
                
                
                
                val finished = try {
                    call.execute().use { response ->
                        if (response.code == 416 && start > 0) return@use true
                        if (response.code != 206 && response.code != 200) {
                            throw IOException("Server error ${response.code}.")
                        }
                        val body = response.body ?: throw IOException("Empty response.")
                        if (firstChunk) {
                            firstChunk = false
                            total = response.header("Content-Range")
                                ?.substringAfter('/')?.toLongOrNull()
                                ?: clen
                                ?: body.contentLength()
                            guardContentType(body.contentType()?.toString().orEmpty())
                            extension = fileExtension(
                                suffix,
                                body.contentType()?.toString().orEmpty()
                            )
                            if (total > 0 && dir.usableSpace < total + MIN_FREE_BYTES) {
                                throw IOException(
                                    context.getString(R.string.download_error_no_space)
                                )
                            }
                        }
                        var chunkBytes = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                start += n
                                chunkBytes += n
                                
                                
                                
                                if (total > 0) {
                                    val pct = (start * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(start, total)
                                    }
                                }
                            }
                        }
                        response.code == 200 && !googleVideo || chunkBytes < CHUNK_SIZE ||
                            (total > 0 && start >= total)
                    }
                } finally {
                    hook?.dispose()
                }
                if (finished) break
            }
        }
        
        
        
        if (total > 0 && tmp.length() != total) {
            throw IOException("Incomplete download (${tmp.length()} of $total bytes).")
        }
        val dst = File(dir, "${track.source}_${track.id}$extension")
        dst.delete()
        if (!tmp.renameTo(dst)) throw IOException("Couldn't save the file.")
        return dst
    } finally {
        tmp.delete()
    }
}


private fun isGoogleVideo(url: String): Boolean = "googlevideo.com" in url


private fun guardContentType(contentType: String) {
    val type = contentType.lowercase()
    if (type.startsWith("text/") || "mpegurl" in type) {
        throw IOException("Not a playable audio file.")
    }
}


private fun fileExtension(suffix: String?, contentType: String): String {
    if (!suffix.isNullOrBlank() && SUFFIX_PATTERN.matches(suffix)) return ".$suffix"
    return extensionFor(contentType)
}


internal fun extensionFor(contentType: String): String {
    val type = contentType.lowercase()
    return when {
        "webm" in type || "opus" in type -> ".webm"
        "mpeg" in type || "mp3" in type -> ".mp3"
        else -> ".m4a"
    }
}

private const val MUSIC_DIR = "music"
private const val BUFFER_SIZE = 8192
private const val CHUNK_SIZE = 1024L * 1024
private const val MIN_FREE_BYTES = 8L * 1024 * 1024
private val SUFFIX_PATTERN = Regex("[a-z0-9]{2,6}")
