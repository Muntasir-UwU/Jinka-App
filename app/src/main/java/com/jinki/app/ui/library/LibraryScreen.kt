package com.jinki.app.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jinki.app.R
import com.jinki.app.data.Track
import com.jinki.app.ui.components.SongRow
import com.jinki.app.ui.components.SongRowData
import com.jinki.app.ui.theme.AlbumTitle
import com.jinki.app.ui.theme.Cream
import com.jinki.app.ui.theme.HighlightPink


private val ScreenBackground = Color.Black


@Composable
fun LibraryScreen(viewModel: LibraryViewModel, snackbar: SnackbarHostState) {
    
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val playingKey by viewModel.playingKey.collectAsStateWithLifecycle()
    val queueList by viewModel.queueList.collectAsStateWithLifecycle()
    val queueCurrentIndex by viewModel.queueCurrentIndex.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.error.collect { snackbar.showSnackbar(it) }
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
        ) {
            Text(
                text = stringResource(R.string.title_library),
                style = MaterialTheme.typography.headlineSmall,
                color = Cream,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            LibraryTabs(
                selectedIndex = selectedTabIndex,
                labels = listOf(
                    stringResource(R.string.library_tab_songs),
                    stringResource(R.string.queue_title)
                ),
                onSelect = { selectedTabIndex = it }
            )

            when (selectedTabIndex) {
                0 -> SongsList(
                    rows = rows,
                    playingKey = playingKey,
                    onSwipeRight = { track -> viewModel.addToQueue(track) },
                    onTap = { index -> viewModel.onTap(index) }
                )
                1 -> QueueTab(
                    queueList = queueList,
                    queueCurrentIndex = queueCurrentIndex
                )
            }
        }
    }
}




@Composable
private fun LibraryTabs(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) Cream else Cream.copy(alpha = 0.6f),
                    label = "libraryTabText"
                )
                val indicatorColor by animateColorAsState(
                    targetValue = if (selected) HighlightPink else Color.Transparent,
                    label = "libraryTabIndicator"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(index) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                indicatorColor,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                            )
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(0.5.dp)
                .background(Cream.copy(alpha = 0.15f))
        )
    }
}



@Composable
private fun SongsList(
    rows: List<Track>,
    playingKey: String?,
    onSwipeRight: (Track) -> Unit,
    onTap: (Int) -> Unit,
) {
    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
            itemsIndexed(rows, key = { _, track -> track.storageKey }) { index, track ->
                SongRowWithSwipe(
                    data = SongRowData(
                        title = track.title,
                        subtitle = track.artist,
                        thumbnailUrl = track.thumbnailUrl,
                        isActive = track.storageKey == playingKey,
                        titleColor = AlbumTitle,
                        subtitleColor = Cream.copy(alpha = 0.65f)
                    ),
                    onSwipeRight = { onSwipeRight(track) },
                    onTap = { onTap(index) }
                )
            }
        }
    }
}



@Composable
private fun QueueTab(
    queueList: List<Track>,
    queueCurrentIndex: Int?,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        itemsIndexed(queueList) { _, track ->
            SongRow(
                data = SongRowData(
                    title = track.title,
                    subtitle = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    isActive = false,
                    titleColor = AlbumTitle,
                    subtitleColor = Cream.copy(alpha = 0.65f)
                ),
                onClick = {  }
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongRowWithSwipe(
    data: SongRowData,
    onSwipeRight: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onSwipeRight()
            false 
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            
            
            
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HighlightPink.copy(alpha = 0.25f))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "Add to queue",
                        style = MaterialTheme.typography.labelLarge,
                        color = Cream
                    )
                }
            }
        }
    ) {
        
        Box(modifier = Modifier.fillMaxWidth().background(ScreenBackground)) {
            SongRow(data = data, onClick = onTap)
        }
    }
}
