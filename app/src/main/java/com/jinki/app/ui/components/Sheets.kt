package com.jinki.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jinki.app.R
import com.jinki.app.data.PlaylistWithCount
import com.jinki.app.data.Track
import com.jinki.app.ui.theme.TextPrimary
import com.jinki.app.ui.theme.TextSecondary

@Composable
private fun SheetMessage(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
}

@Composable
fun TimerSheetContent(
    remainingSeconds: Long,
    selectedMinutes: Int?,
    customMinutes: String,
    onSelectMinutes: (Int) -> Unit,
    onCustomMinutesChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.timer_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (remainingSeconds > 0) {
                stringResource(
                    R.string.timer_stops_in,
                    (remainingSeconds / 60).toInt(),
                    (remainingSeconds % 60).toInt()
                )
            } else {
                stringResource(R.string.timer_sheet_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerPresets.forEach { mins ->
                FilterChip(
                    selected = selectedMinutes == mins,
                    onClick = { onSelectMinutes(mins) },
                    label = { Text(stringResource(R.string.timer_chip, mins)) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = customMinutes,
            onValueChange = onCustomMinutesChange,
            label = { Text(stringResource(R.string.timer_custom_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onCancelTimer) {
                Text(stringResource(R.string.timer_cancel))
            }
            Button(onClick = onStart) {
                Text(stringResource(R.string.timer_start))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val TimerPresets = listOf(15, 30, 45, 60)

@Composable
fun QueueSheetContent(
    queue: List<Track>,
    currentIndex: Int,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = stringResource(R.string.queue_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            itemsIndexed(queue, key = { index, track -> "$index-${track.storageKey}" }) { index, track ->
                SongRow(
                    data = SongRowData(
                        title = track.title,
                        subtitle = track.artist,
                        thumbnailUrl = track.thumbnailUrl,
                        isActive = index == currentIndex
                    ),
                    onClick = { onJump(index) },
                    trailing = {
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_remove),
                                tint = TextSecondary
                            )
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PlaylistSheetContent(
    playlists: List<PlaylistWithCount>,
    memberIds: Set<Long>,
    onToggle: (Long) -> Unit,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.playlist_add_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        if (playlists.isEmpty()) {
            SheetMessage(stringResource(R.string.playlist_empty))
        } else {
            LazyColumn {
                items(playlists, key = { it.playlist.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(item.playlist.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.playlist.id in memberIds,
                            onCheckedChange = { onToggle(item.playlist.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.playlist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.playlist_count,
                                    item.entryCount,
                                    item.entryCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = onNewNameChange,
                placeholder = { Text(stringResource(R.string.playlist_new_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onCreate, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.playlist_create))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MessageSheetContent(
    messages: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.message_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        if (messages.isEmpty()) {
            SheetMessage(stringResource(R.string.message_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(messages) { _, message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.message_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onSend, enabled = draft.isNotBlank()) {
                Text(stringResource(R.string.action_send))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}