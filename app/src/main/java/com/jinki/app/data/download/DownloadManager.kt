package com.jinki.app.data.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jinki.app.data.UserPrefs
import com.jinki.app.data.source.SearchHit
import com.jinki.app.data.source.SourceRef
import com.jinki.app.data.source.toSourceRef
import com.jinki.app.util.PermissionUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: UserPrefs
) {

    private val work = WorkManager.getInstance(context)

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val watchers = ConcurrentHashMap<String, Job>()

    fun download(hit: SearchHit) {
        val key = hit.storageKey
        when (_states.value[key]?.status) {
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> return
            else -> Unit
        }
        update(key, DownloadState(hit.toSourceRef(), DownloadStatus.QUEUED))
        ensureWatcher(key)
        scope.launch {
            val wifiOnly = try { prefs.wifiOnly.first() } catch (_: Exception) { false }

            
            
            val useExpedited = if (PermissionUtil.hasNotificationPermission(context)) {
                true
            } else {
                Log.w("DownloadManager", "POST_NOTIFICATIONS permission not granted, falling back to non-expedited work")
                false
            }

            val input = Data.Builder()
                .putString(KEY_STORAGE, key)
                .putString(KEY_SOURCE, hit.source)
                .putString(KEY_ID, hit.id)
                .putString(KEY_TITLE, hit.title)
                .putString(KEY_ARTIST, hit.artist)
                .putString(KEY_THUMB, hit.thumbnailUrl)
                .apply { hit.pageUrl?.let { putString(KEY_PAGE, it) } }
                .build()
            val requestBuilder = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(input)
                .addTag(key)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                        )
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)

            if (useExpedited) {
                requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            val request = requestBuilder.build()
            try {
                work.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                Log.e("DownloadManager", "Failed to enqueue download work", e)
                update(key, DownloadState(
                    hit.toSourceRef(),
                    DownloadStatus.FAILED,
                    error = "Failed to start download: ${e.message}"
                ))
            }
        }
    }

    fun cancel(storageKey: String) {
        work.cancelUniqueWork(storageKey)
    }

    private fun ensureWatcher(key: String) {
        watchers.computeIfAbsent(key) {
            scope.launch {
                work.getWorkInfosByTagFlow(key).collect { infos ->
                    val info = infos.maxByOrNull { it.runAttemptCount } ?: return@collect
                    update(key, info.toDownloadState(state(key)))
                    if (info.state.isFinished) watchers.remove(key)
                }
            }
        }
    }

    private fun state(key: String): DownloadState =
        _states.value[key] ?: DownloadState(SourceRef("", ""))

    private fun update(key: String, state: DownloadState) {
        _states.update { it + (key to state) }
    }
}

private fun WorkInfo.toDownloadState(prev: DownloadState): DownloadState = when (state) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
        prev.copy(status = DownloadStatus.QUEUED, progress = 0f)
    WorkInfo.State.RUNNING -> {
        val written = progress.getLong(PROG_BYTES, prev.bytesWritten)
        val total = progress.getLong(PROG_TOTAL, prev.totalBytes)
        prev.copy(
            status = DownloadStatus.DOWNLOADING,
            progress = if (total > 0) written.toFloat() / total else 0f,
            bytesWritten = written,
            totalBytes = total
        )
    }
    WorkInfo.State.SUCCEEDED -> prev.copy(
        status = DownloadStatus.DONE,
        progress = 1f,
        bytesWritten = outputData.getLong(OUT_BYTES, prev.bytesWritten),
        totalBytes = outputData.getLong(OUT_TOTAL, prev.totalBytes)
    )
    WorkInfo.State.FAILED -> prev.copy(
        status = DownloadStatus.FAILED,
        error = outputData.getString(OUT_ERROR) ?: "Download failed."
    )
    WorkInfo.State.CANCELLED -> prev.copy(status = DownloadStatus.CANCELLED)
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    DONE,
    FAILED,
    CANCELLED
}

data class DownloadState(
    val ref: SourceRef,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val bytesWritten: Long = 0L,
    val totalBytes: Long = -1L,
    val error: String? = null
)
