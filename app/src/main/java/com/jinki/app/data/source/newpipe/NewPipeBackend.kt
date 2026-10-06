package com.jinki.app.data.source.newpipe

import com.jinki.app.data.artwork.soundCloudArtworkCandidates
import com.jinki.app.data.artwork.youtubeArtworkCandidates
import com.jinki.app.data.source.SOURCE_SOUNDCLOUD
import com.jinki.app.data.source.SOURCE_YOUTUBE
import com.jinki.app.data.source.SearchHit
import com.jinki.app.data.source.SearchSource
import com.jinki.app.data.source.SourceException
import com.jinki.app.data.source.SourceRef
import com.jinki.app.data.source.StreamResolver
import com.jinki.app.data.source.ResolvedStream
import com.jinki.app.data.source.isVideoId
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.regex.Pattern


class NewPipeBackend(
    private val http: OkHttpClient = defaultHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO
) : SearchSource, StreamResolver {

    override val key: String = "newpipe"

    override fun supports(source: String): Boolean = source in SUPPORTED

    @Volatile
    private var initialised = false

    
    private val services: List<BackendService> by lazy { buildServices() }

    override suspend fun search(query: String, page: Int): List<SearchHit> = withContext(io) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext emptyList()
        
        
        
        if (page > 0) throw SourceException("Only the first page of results is supported.")

        val hits = LinkedHashMap<String, SearchHit>()
        var failure: SourceException? = null

        for (backend in services) {
            val found = try {
                backend.search(term)
            } catch (e: InterruptedException) {
                throw CancellationException("Search cancelled.", e)
            } catch (e: ExtractionException) {
                failure = failure ?: describe(e)
                continue
            } catch (e: IOException) {
                failure = failure ?: SourceException("Network error while searching.", e)
                continue
            }
            for (hit in found) hits.putIfAbsent(hit.storageKey, hit)
        }

        
        
        if (hits.isEmpty() && failure != null) throw failure
        hits.values.toList()
    }

    override suspend fun resolve(ref: SourceRef): ResolvedStream = withContext(io) {
        val service = when (ref.source) {
            SOURCE_YOUTUBE -> ServiceList.YouTube
            SOURCE_SOUNDCLOUD -> ServiceList.SoundCloud
            else -> throw SourceException("Nothing can play ${ref.source} tracks.")
        }

        val info = try {
            StreamInfo.getInfo(service, pageUrlFor(ref))
        } catch (e: InterruptedException) {
            throw CancellationException("Resolve cancelled.", e)
        } catch (e: ExtractionException) {
            throw describe(e)
        } catch (e: IOException) {
            throw SourceException("Network error while loading this track.", e)
        }

        val audio = info.audioStreams.bestAudio()
            ?: throw SourceException("No playable audio was found for this track.")

        
        
        
        
        
        
        ResolvedStream(
            url = audio.content.orEmpty(),
            title = info.name.orEmpty(),
            artist = info.uploaderName.orEmpty(),
            thumbnailUrl = bestArtwork(ref, info),
            durationSeconds = info.duration.toInt(),
            formatId = audio.formatId,
            averageBitrate = audio.averageBitrate,
            suffix = audio.format?.suffix
        )
    }

    
    private fun bestArtwork(ref: SourceRef, info: StreamInfo): String {
        val ladder = youtubeArtworkCandidates(ref.id)
            .takeIf { ref.source == SOURCE_YOUTUBE }
            .orEmpty()
        return (ladder + largestThumbnail(info.thumbnails))
            .firstOrNull { it.isNotBlank() }
            ?: fallbackArtwork(ref)
    }

    
    private fun fallbackArtwork(ref: SourceRef): String =
        if (ref.source == SOURCE_YOUTUBE && isVideoId(ref.id)) {
            "https://i.ytimg.com/vi/${ref.id}/hqdefault.jpg"
        } else {
            ""
        }

    
    private fun pageUrlFor(ref: SourceRef): String = ref.pageUrl ?: when (ref.source) {
        SOURCE_YOUTUBE -> "https://i.ytimg.com/vi/${ref.id}/hqdefault.jpg"
        
        
        SOURCE_SOUNDCLOUD -> ref.id.takeIf { it.startsWith("http") }
            ?: throw SourceException(
                "This ${ref.source} track has no permalink, so it can't be played."
            )
        else -> throw SourceException(
            "This ${ref.source} track has no permalink, so it can't be played."
        )
    }

    private fun buildServices(): List<BackendService> {
        initialise()
        return listOf(
            
            
            
            BackendService(
                SOURCE_YOUTUBE,
                ServiceList.YouTube,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                ::youtubeHit
            ),
            
            
            BackendService(SOURCE_SOUNDCLOUD, ServiceList.SoundCloud, emptyList(), ::soundcloudHit)
        )
    }

    
    @Synchronized
    private fun initialise() {
        if (initialised) return
        NewPipe.init(OkHttpDownloader(http))
        initialised = true
    }

    
    private fun describe(e: ExtractionException): SourceException = SourceException(
        when (e) {
            is ContentNotAvailableException -> "That track isn't available any more."
            is AgeRestrictedContentException -> "That track is age-restricted."
            is GeographicRestrictionException -> "That track isn't available in this region."
            is PrivateContentException -> "That track is private."
            is PaidContentException -> "That track is paid content."
            else -> e.message?.takeIf { it.isNotBlank() } ?: "Extraction failed."
        },
        e
    )

    companion object {
        val SUPPORTED = setOf(SOURCE_YOUTUBE, SOURCE_SOUNDCLOUD)
    }
}


private class BackendService(
    val source: String,
    val service: StreamingService,
    val filter: List<String>,
    val map: (InfoItem) -> SearchHit?
) {
    fun search(query: String): List<SearchHit> {
        val handler = service.searchQHFactory.fromQuery(query, filter, "")
        return SearchInfo.getInfo(service, handler).relatedItems.mapNotNull { item -> map(item) }
    }
}


private fun youtubeHit(item: InfoItem): SearchHit? {
    val stream = item as? StreamInfoItem ?: return null
    if (stream.infoType != InfoItem.InfoType.STREAM) return null
    val id = videoIdFromUrl(stream.url) ?: return null

    val ladder = youtubeArtworkCandidates(id)
    val extracted = largestThumbnail(stream.thumbnails)
    return SearchHit(
        id = id,
        source = SOURCE_YOUTUBE,
        title = cleanTitle(stream.name),
        artist = stream.uploaderName.orEmpty(),
        thumbnailUrl = (ladder + extracted).firstOrNull { it.isNotBlank() }.orEmpty(),
        thumbnailHighUrl = ladder.getOrNull(1) ?: extracted,
        thumbnailFallbackUrl = ladder.getOrNull(2) ?: extracted,
        durationSeconds = stream.duration.toInt(),
        pageUrl = stream.url
    )
}


private fun soundcloudHit(item: InfoItem): SearchHit? {
    val stream = item as? StreamInfoItem ?: return null
    if (stream.infoType != InfoItem.InfoType.STREAM) return null
    val permalink = stream.url
    if (permalink.isBlank()) return null

    val ladder = soundCloudArtworkCandidates(largestThumbnail(stream.thumbnails))
    return SearchHit(
        id = permalink,
        source = SOURCE_SOUNDCLOUD,
        title = cleanTitle(stream.name),
        artist = stream.uploaderName.orEmpty(),
        thumbnailUrl = ladder.firstOrNull { it.isNotBlank() }.orEmpty(),
        thumbnailHighUrl = ladder.getOrNull(1).orEmpty(),
        thumbnailFallbackUrl = ladder.getOrNull(2).orEmpty(),
        durationSeconds = stream.duration.toInt(),
        pageUrl = permalink
    )
}


private fun largestThumbnail(thumbnails: List<Image>): String = thumbnails
    .maxWithOrNull(
        compareBy<Image> { RESOLUTION_RANK[it.estimatedResolutionLevel] ?: 0 }
            .thenBy { knownArea(it) }
    )
    ?.url
    .orEmpty()


private fun knownArea(image: Image): Long {
    val width = image.width
    val height = image.height
    return if (width <= 0 || height <= 0) 0L else width.toLong() * height.toLong()
}


private fun cleanTitle(raw: String): String {
    var title = raw.trim()

    
    title = Regex("""\s*[\(\[](official|feat\.?|ft\.?|featuring|with|remix|version|audio|video|lyrics|visualizer|music|hd|hq|live|acoustic|explicit|clean)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE)
        .replace(title, "")

    
    title = Regex("""\s*[-|]\s*YouTube Music\s*$""", RegexOption.IGNORE_CASE).replace(title, "")
    title = Regex("""\s+YouTube Music\s*$""", RegexOption.IGNORE_CASE).replace(title, "")

    
    title = Regex("""\s+(Official|official)\s*$""").replace(title, "")

    
    title = title.replace(Regex("""\s+"""), " ").trim()

    return title
}



private fun videoIdFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return WATCH_ID.find(url)?.groupValues?.get(1)
        ?: SHORT_ID.find(url)?.groupValues?.get(1)
}

private val WATCH_ID = Regex("[?&]v=([A-Za-z0-9_-]{11})")
private val SHORT_ID = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")


internal fun List<AudioStream>.bestAudio(): AudioStream? =
    filter { !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        .maxByOrNull { it.averageBitrate }


private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private val RESOLUTION_RANK = mapOf(
    Image.ResolutionLevel.UNKNOWN to 0,
    Image.ResolutionLevel.LOW to 1,
    Image.ResolutionLevel.MEDIUM to 2,
    Image.ResolutionLevel.HIGH to 3
)
