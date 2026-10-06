package com.jinki.app.ui.user

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinki.app.R
import com.jinki.app.ui.components.MessageSheetContent
import com.jinki.app.ui.components.TimerSheetContent
import com.jinki.app.ui.theme.Accent
import com.jinki.app.ui.theme.Cream


private val MessageBlue = Color(0xFFA0D8F4)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(viewModel: UserViewModel, snackbar: SnackbarHostState) {
    val greeting by viewModel.greeting.collectAsStateWithLifecycle()
    val todaySeconds by viewModel.todaySeconds.collectAsStateWithLifecycle()
    val timerRemaining by viewModel.timerRemaining.collectAsStateWithLifecycle()
    val timerActive by viewModel.timerActive.collectAsStateWithLifecycle()
    val pokeCount by viewModel.pokeCount.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val partnerSeconds by viewModel.partnerSeconds.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()

    var showTimer by remember { mutableStateOf(false) }
    var showMessage by remember { mutableStateOf(false) }
    var selectedMinutes: Int? by remember { mutableStateOf<Int?>(null) }
    var customMinutes by remember { mutableStateOf("") }
    var pendingToast by remember { mutableStateOf<Toast?>(null) }

    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setPhotoUri(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { pendingToast = it }
    }
    
    
    val toastText = pendingToast?.let { stringResource(it.resId, *it.args.toTypedArray()) }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            snackbar.showSnackbar(toastText)
            pendingToast = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        
        ProfileHero(
            name = greeting.name,
            photoUri = photoUri,
            todaySeconds = todaySeconds,
            onChangePhoto = { photoPicker.launch(arrayOf("image/*")) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            LoveCard(partnerSeconds = partnerSeconds)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ActionCard(
                    icon = Icons.Outlined.Timer,
                    title = "Timer",
                    subtitle = if (timerActive) formatClock(timerRemaining.toInt()) else "Sleep timer",
                    active = timerActive,
                    onClick = { showTimer = true },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Outlined.TouchApp,
                    title = "Poke",
                    subtitle = "Say hi",
                    active = false,
                    onClick = { viewModel.poke() },
                    badge = if (pokeCount > 0) pokeCount.toString() else null,
                    modifier = Modifier.weight(1f)
                )
            }

            ActionCard(
                icon = Icons.AutoMirrored.Outlined.Send,
                title = "Message",
                subtitle = messages.lastOrNull()?.toString() ?: "Send a note",
                active = false,
                onClick = { showMessage = true },
                modifier = Modifier.fillMaxWidth()
            )

            if (timerActive) {
                OutlinedButton(
                    onClick = { viewModel.cancelTimer() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel timer") }
            }
        }
    }

    if (showTimer) {
        ModalBottomSheet(
            onDismissRequest = { showTimer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TimerSheetContent(
                remainingSeconds = timerRemaining,
                selectedMinutes = selectedMinutes,
                customMinutes = customMinutes,
                onSelectMinutes = { selectedMinutes = it },
                onCustomMinutesChange = { customMinutes = it },
                onStart = {
                    viewModel.startTimer(selectedMinutes, customMinutes)
                    showTimer = false
                },
                onCancelTimer = {
                    viewModel.cancelTimer()
                    showTimer = false
                }
            )
        }
    }

    if (showMessage) {
        ModalBottomSheet(
            onDismissRequest = { showMessage = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            MessageSheetContent(
                messages = messages,
                draft = draft,
                onDraftChange = { viewModel.onDraftChange(it) },
                onSend = {
                    viewModel.sendMessage()
                    showMessage = false
                }
            )
        }
    }
}

@Composable
private fun ProfileHero(
    name: String,
    photoUri: String?,
    todaySeconds: Int,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "",
                fontFamily = FontFamily.Serif,
                fontSize = 120.sp,
                color = Cream.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        IconButton(
            onClick = onChangePhoto,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = "Change photo",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.user_today_listened),
                style = MaterialTheme.typography.labelMedium,
                color = Cream.copy(alpha = 0.7f)
            )
            Text(
                text = formatClock(todaySeconds),
                style = MaterialTheme.typography.displaySmall,
                color = Cream
            )
        }
    }
}


@Composable
private fun LoveCard(partnerSeconds: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Love?",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            color = Cream
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "His listening today",
                style = MaterialTheme.typography.labelMedium,
                color = Cream.copy(alpha = 0.6f)
            )
            Text(
                text = partnerSeconds?.let { formatClock(it) } ?: "--:--:--",
                style = MaterialTheme.typography.headlineMedium,
                color = Cream
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Cream,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = Cream)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(badge) }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Accent else Cream.copy(alpha = 0.6f)
            )
        }
    }
}
