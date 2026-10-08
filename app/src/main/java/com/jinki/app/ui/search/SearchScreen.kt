package com.jinki.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jinki.app.R
import com.jinki.app.ui.components.PlaylistSheetContent
import com.jinki.app.ui.components.SongRow
import com.jinki.app.ui.components.SongRowData
import com.jinki.app.ui.components.formatDuration
import com.jinki.app.ui.theme.AlbumTitle
import com.jinki.app.ui.theme.Cream
import com.jinki.app.ui.theme.HighlightPink


private val ScreenBackground = Color.Black


private val ActivePink = Color(0xFFF69CC7)


private val BottomNavClearance = 104.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel, snackbar: SnackbarHostState) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val searching by viewModel.isSearching.collectAsStateWithLifecycle()
    val playingKey by viewModel.playingKey.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val playlistTarget by viewModel.playlistTarget.collectAsStateWithLifecycle()
    val playlistSummaries by viewModel.playlistSummaries.collectAsStateWithLifecycle()
    val memberIds by viewModel.memberIds.collectAsStateWithLifecycle()

    var sheetNewName by remember { mutableStateOf("") }

    val errorDetail = error?.let {
        if (it.trackTitle != null) {
            stringResource(R.string.search_download_error, it.trackTitle, it.message)
        } else {
            stringResource(R.string.search_error_detail, it.message)
        }
    }
    LaunchedEffect(errorDetail) {
        if (errorDetail != null) {
            snackbar.showSnackbar(errorDetail)
            viewModel.clearError()
        }
    }

    
    
    
    val pageColors = MaterialTheme.colorScheme.copy(
        background = ScreenBackground,
        surface = ScreenBackground,
        surfaceVariant = ScreenBackground
    )

    MaterialTheme(
        colorScheme = pageColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBackground)
                // adjustResize shrinks the window, but without imePadding the
                // field and list sit under the keyboard frame on devices
                // where the IME doesn't resize the app window.
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            
            
            
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = ActivePink,
                    backgroundColor = ActivePink.copy(alpha = 0.4f)
                )
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint_query)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = viewModel::clearQuery) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Cream,
                        unfocusedTextColor = Cream,
                        cursorColor = ActivePink,
                        focusedBorderColor = HighlightPink,
                        unfocusedBorderColor = Cream.copy(alpha = 0.35f),
                        focusedLeadingIconColor = Cream,
                        unfocusedLeadingIconColor = Cream.copy(alpha = 0.7f),
                        focusedTrailingIconColor = Cream,
                        unfocusedTrailingIconColor = Cream.copy(alpha = 0.7f),
                        focusedPlaceholderColor = Cream.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = Cream.copy(alpha = 0.5f)
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    searching -> stringResource(R.string.search_hint_searching)
                    rows.isEmpty() && query.isNotBlank() ->
                        stringResource(R.string.search_hint_no_results)
                    else -> stringResource(R.string.search_hint_default)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(8.dp))
            if (searching && rows.isEmpty()) {
                
                SearchSkeletonList(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = BottomNavClearance)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScreenBackground),
                    contentPadding = PaddingValues(bottom = BottomNavClearance)
                ) {
                    items(rows, key = { it.hit.storageKey }) { row ->
                        SongRow(
                            data = SongRowData(
                                title = cleanTitle(row.hit.title),
                                subtitle = cleanArtist(row.hit.artist),
                                thumbnailUrl = row.hit.thumbnailUrl,
                                thumbnailFallbackUrl = row.hit.thumbnailFallbackUrl,
                                titleColor = AlbumTitle,
                                subtitleColor = Cream.copy(alpha = 0.65f),
                                status = when (row.status) {
                                    RowStatus.IDLE -> formatDuration(row.hit.durationSeconds)
                                    RowStatus.SAVING -> stringResource(R.string.status_saving)
                                    RowStatus.FAILED -> stringResource(R.string.status_failed)
                                    RowStatus.SAVED -> stringResource(R.string.status_saved)
                                        
                                        
                                        .trimEnd('✓', '✔', '√', '☑', ' ')
                                },
                                isActive = row.hit.storageKey == playingKey,
                                statusEmphasis = row.status == RowStatus.SAVED
                            ),
                            onClick = { viewModel.onTap(row.hit) },
                            onLongPress = { viewModel.openPlaylistSheet(row.hit) }
                        )
                    }
                }
            }
        }
    }

    if (playlistTarget != null) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.closePlaylistSheet()
                sheetNewName = ""
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PlaylistSheetContent(
                playlists = playlistSummaries,
                memberIds = memberIds,
                onToggle = viewModel::togglePlaylistMembership,
                newName = sheetNewName,
                onNewNameChange = { sheetNewName = it },
                onCreate = {
                    viewModel.createPlaylist(sheetNewName)
                    sheetNewName = ""
                }
            )
        }
    }
}
