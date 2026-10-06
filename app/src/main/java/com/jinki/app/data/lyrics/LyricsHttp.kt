package com.jinki.app.data.lyrics

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject


sealed interface SourceReply<out T> {
    
    data class Hit<out T>(val value: T) : SourceReply<T>

    
    data object Miss : SourceReply<Nothing>

    
    data object Failure : SourceReply<Nothing>
}

internal const val USER_AGENT = "Jinki/1.0 (Android)"
private const val DEFAULT_RETRY_AFTER_SECONDS = 60L


internal inline fun <T> SourceReply<String>.parse(
    transform: (String) -> SourceReply<T>
): SourceReply<T> = when (this) {
    is SourceReply.Hit -> transform(value)
    SourceReply.Miss -> SourceReply.Miss
    SourceReply.Failure -> SourceReply.Failure
}


private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { this@await.cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })
}


internal suspend fun OkHttpClient.fetchText(
    url: HttpUrl,
    onRateLimited: ((retryAfterSeconds: Long) -> Unit)? = null
): SourceReply<String> {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .build()
    return try {
        newCall(request).await().use<Response, SourceReply<String>> { response ->
            when {
                response.isSuccessful -> {
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) SourceReply.Failure else SourceReply.Hit(body)
                }
                response.code == 404 -> SourceReply.Miss
                else -> {
                    if (response.code == 429) {
                        onRateLimited?.invoke(
                            response.header("Retry-After")?.toLongOrNull()
                                ?: DEFAULT_RETRY_AFTER_SECONDS
                        )
                    }
                    SourceReply.Failure
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SourceReply.Failure
    }
}


internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
