package com.jinki.app.player

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.jinki.app.data.source.EXTRACTION_USER_AGENT


internal fun buildMediaSourceFactory(context: Context): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(DefaultDataSource.Factory(context, buildHttpDataSourceFactory()))
        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(MIN_LOADABLE_RETRY_COUNT))


internal fun buildHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent(EXTRACTION_USER_AGENT)
        
        
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)


internal fun buildLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        MIN_BUFFER_MS,
        MAX_BUFFER_MS,
        BUFFER_FOR_PLAYBACK_MS,
        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()


private const val MIN_LOADABLE_RETRY_COUNT = 3

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

private const val MIN_BUFFER_MS = 15_000
private const val MAX_BUFFER_MS = 120_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_000
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
