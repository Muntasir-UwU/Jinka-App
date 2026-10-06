package com.jinki.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jinki.app.ui.theme.TextPrimary
import com.jinki.app.ui.theme.TextSecondary


data class SongRowData(
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val thumbnailFallbackUrl: String? = null,
    val isActive: Boolean = false,
    val titleColor: Color = TextPrimary,
    val subtitleColor: Color = TextSecondary,
    val status: String? = null,
    val statusEmphasis: Boolean = false
)


private val ActiveRowTint = Color(0xFFF69CC7).copy(alpha = 0.14f)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    data: SongRowData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val statusColor = if (data.statusEmphasis) data.titleColor else data.subtitleColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (data.isActive) ActiveRowTint else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .defaultMinSize(minHeight = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowArtwork(data.thumbnailUrl, data.thumbnailFallbackUrl)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.bodyLarge,
                color = data.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (data.subtitle.isNotBlank()) {
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = data.subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!data.status.isNullOrBlank()) {
            Text(
                text = data.status,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                maxLines = 1
            )
        }
        trailing?.invoke()
    }
}


@Composable
private fun RowArtwork(primary: String?, fallback: String?) {
    val candidates = remember(primary, fallback) {
        listOfNotNull(primary, fallback).filter { it.isNotBlank() }
    }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val model = candidates.getOrNull(index)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { if (index < candidates.lastIndex) index++ }
            )
        }
    }
}


fun formatDuration(seconds: Number?): String {
    val total = seconds?.toLong() ?: return ""
    if (total <= 0L) return ""
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secs"
    } else {
        "$minutes:$secs"
    }
}
