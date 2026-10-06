package com.jinki.app.data.source.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import com.jinki.app.data.source.EXTRACTION_USER_AGENT
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response


internal class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val sentBody = request.dataToSend()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(
                request.httpMethod(),
                when {
                    request.httpMethod() == "GET" || request.httpMethod() == "HEAD" -> null
                    sentBody != null -> sentBody.toRequestBody()
                    else -> ByteArray(0).toRequestBody()
                }
            )
        request.headers().forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.headers().keys.none { it.equals(USER_AGENT_HEADER, ignoreCase = true) }) {
            builder.header(USER_AGENT_HEADER, EXTRACTION_USER_AGENT)
        }
        client.newCall(builder.build()).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                
                
                
                response.request.url.toString()
            )
        }
    }

    companion object {
        const val USER_AGENT_HEADER = "User-Agent"
    }
}
