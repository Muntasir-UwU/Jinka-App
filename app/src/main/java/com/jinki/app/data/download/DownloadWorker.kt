package com.jinki.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import com.jinki.app.JinkiApplication
import com.jinki.app.R
import com.jinki.app.data.JinkaDatabase
import com.jinki.app.data.Track
import com.jinki.app.data.TrackRepository
import com.jinki.app.data.source.SourceException
import com.jinki.app.data.source.SourceRef
import com.jinki.app.data.source.SourceRegistry
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (inputData.getString(KEY_STORAGE) == null) return Result.failure()
        setForeground(getForegroundInfo())
        return try {
            val source = inputData.getString(KEY_SOURCE).orEmpty()
            val id = inputData.getString(KEY_ID).orEmpty()
            if (source.isBlank() || id.isBlank()) return Result.failure()
            val stream = SourceRegistry.resolve(
                SourceRef(source, id, inputData.getString(KEY_PAGE))
            )
            val track = Track(
                id = id,
                source = source,
                title = inputData.getString(KEY_TITLE).orEmpty(),
                artist = inputData.getString(KEY_ARTIST).orEmpty(),
                thumbnailUrl = inputData.getString(KEY_THUMB).orEmpty(),
                streamUrl = stream.url,
                pageUrl = inputData.getString(KEY_PAGE)
            )
            val app = applicationContext as JinkiApplication
            val file = fetchTrackToFile(
                applicationContext,
                app.container.http,
                track,
                stream.suffix
            ) { written, total ->
                setProgress(workDataOf(PROG_BYTES to written, PROG_TOTAL to total))
            }
            val db = JinkaDatabase.get(applicationContext)
            TrackRepository(db.libraryDao(), db.playbackDao()).save(track, file.absolutePath)
            Result.success(workDataOf(OUT_BYTES to file.length(), OUT_TOTAL to file.length()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceException) {
            Result.failure(workDataOf(OUT_ERROR to (e.message ?: "Download failed.")))
        } catch (e: IOException) {
            
            
            val isTransient = e is ConnectException
                || e is SocketTimeoutException
                || e is UnknownHostException
                || e is SSLException
                || (e.cause is ConnectException)
                || (e.cause is SocketTimeoutException)
                || (e.cause is UnknownHostException)
                || (e.cause is SSLException)

            if (!isTransient || runAttemptCount >= MAX_ATTEMPTS - 1) {
                Result.failure(workDataOf(OUT_ERROR to (e.message ?: "Download failed.")))
            } else {
                Result.retry()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.app_name)
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        val id = NOTIFICATION_BASE_ID +
            ((inputData.getString(KEY_STORAGE)?.hashCode() ?: 0) and 0xFFF)
        return ForegroundInfo(id, notification)
    }
}

internal const val KEY_STORAGE = "storage_key"
internal const val KEY_SOURCE = "source"
internal const val KEY_ID = "id"
internal const val KEY_TITLE = "title"
internal const val KEY_ARTIST = "artist"
internal const val KEY_THUMB = "thumbnail"
internal const val KEY_PAGE = "page_url"
internal const val PROG_BYTES = "bytes_written"
internal const val PROG_TOTAL = "bytes_total"
internal const val OUT_ERROR = "error"
internal const val OUT_BYTES = "bytes_written"
internal const val OUT_TOTAL = "bytes_total"
internal const val MAX_ATTEMPTS = 3

private const val CHANNEL_ID = "downloads"
private const val NOTIFICATION_BASE_ID = 2000
