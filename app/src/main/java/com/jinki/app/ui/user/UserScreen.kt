package com.jinki.app.ui.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import coil.request.ImageRequest
import com.jinki.app.ui.components.Avatar
import com.jinki.app.ui.components.AvatarInitial
import com.jinki.app.ui.components.AvatarPhoto
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jinki.app.MainActivity
import com.jinki.app.R
import com.jinki.app.ui.components.TimerSheetContent
import com.jinki.app.ui.theme.Cream

// ───────────────────────── Palette ─────────────────────────
// Pink + light blue accents, cream + ash text. No green / orange.

private val Pink = Color(0xFFF4B6C8)
private val LightBlue = Color(0xFFA0D8F4)
private val Ash = Color(0xFF9B9B9B)
private val OnTint = Color(0xFF2A2326)
private val CardFill = Color.White.copy(alpha = 0.08f)
private val SheetFill = Color(0xFF1B1A1A)
private val DialogFill = Color(0xFF242424)

// ───────────────────────── Models ─────────────────────────
// Move these to their own file if you prefer.

data class ActiveUser(
    val id: String,
    val name: String,
    val photoUri: String? = null,
    val nowPlaying: String? = null,
    val isActive: Boolean = false,
    val lastSeen: String? = null,
    val avatarB64: String? = null,
    val avatarUpdatedAt: Long? = null
)

/*
 * UserViewModel additions this screen expects:
 *
 *   val activeUsers: StateFlow<List<ActiveUser>>
 *
 * Removed (no longer used here): messages, draft, onDraftChange, sendMessage,
 * and the whole sync flow (syncedWith, syncState, syncWithUser, stopSync,
 * resetSyncState, SyncState, SyncPasswordDialog, SyncedBanner, LoveCard).
 */

// ───────────────────────── Screen ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(viewModel: UserViewModel, snackbar: SnackbarHostState) {
    val greeting by viewModel.greeting.collectAsStateWithLifecycle()
    val todaySeconds by viewModel.todaySeconds.collectAsStateWithLifecycle()
    val timerRemaining by viewModel.timerRemaining.collectAsStateWithLifecycle()
    val timerActive by viewModel.timerActive.collectAsStateWithLifecycle()
    val pokeCount by viewModel.pokeCount.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val avatarSaving by viewModel.avatarSaving.collectAsStateWithLifecycle()
    val avatarVersion by viewModel.avatarVersion.collectAsStateWithLifecycle()
    val avatarBitmaps by viewModel.otherAvatars.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()

    var showTimer by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var pokePickMode by remember { mutableStateOf(false) }
    var selectedMinutes: Int? by remember { mutableStateOf<Int?>(null) }
    var customMinutes by remember { mutableStateOf("") }
    var pendingToast by remember { mutableStateOf<Toast?>(null) }

    val context = LocalContext.current
    // Badge reset + one-time notification permission, off cold start: only
    // when the You screen is actually opened.
    LaunchedEffect(Unit) {
        viewModel.clearPokes()
        (context as? MainActivity)?.maybeRequestPokePermission()
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        // Copied to app-private storage right away (ViewModel), so the
        // temporary gallery Uri never needs to outlive this callback.
        if (uri != null) viewModel.saveAvatar(context, uri)
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
            avatarVersion = avatarVersion,
            saving = avatarSaving,
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
                    onClick = {
                        pokePickMode = true
                        showUsers = true
                        (context as? MainActivity)?.maybeRequestPokePermission()
                    },
                    badge = if (pokeCount > 0) pokeCount.toString() else null,
                    modifier = Modifier.weight(1f)
                )
            }

            ActiveUsersCard(
                users = activeUsers,
                bitmaps = avatarBitmaps,
                onClick = {
                    pokePickMode = false
                    showUsers = true
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (timerActive) {
                OutlinedButton(
                    onClick = { viewModel.cancelTimer() },
                    border = BorderStroke(1.dp, Pink.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Pink),
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

    if (showUsers) {
        ModalBottomSheet(
            onDismissRequest = {
                showUsers = false
                pokePickMode = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetFill
        ) {
            ActiveUsersSheetContent(
                users = activeUsers,
                bitmaps = avatarBitmaps,
                pickMode = pokePickMode,
                onUserClick = { user ->
                    if (pokePickMode) {
                        viewModel.pokeUser(user.id, user.name)
                        (context as? MainActivity)?.maybeRequestPokePermission()
                    }
                    showUsers = false
                    pokePickMode = false
                }
            )
        }
    }

}

// ───────────────────────── Hero ─────────────────────────

@Composable
private fun ProfileHero(
    name: String,
    photoUri: String?,
    todaySeconds: Int,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    avatarVersion: Long = 0
) {
    Box(
        modifier = modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        // Letter stays behind the photo: placeholder while loading,
        // fallback if the file is gone.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AvatarInitial(
                name = name,
                fontSize = 120.sp,
                color = Pink.copy(alpha = 0.85f),
                fontFamily = FontFamily.Serif
            )
            if (photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(photoUri))
                        .memoryCacheKey("$photoUri:$avatarVersion")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (saving) {
            CircularProgressIndicator(
                color = Cream,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
                )
        )

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
                tint = Cream
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
                color = Ash
            )
            Text(
                text = formatClock(todaySeconds),
                style = MaterialTheme.typography.displaySmall,
                color = Cream
            )
        }
    }
}

// ───────────────────────── Cards ─────────────────────────

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
            .background(CardFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LightBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = Cream)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = Pink, contentColor = OnTint) { Text(badge) }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Pink else Ash
            )
        }
    }
}

@Composable
private fun ActiveUsersCard(
    users: List<ActiveUser>,
    bitmaps: Map<String, Bitmap>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = users.filter { it.isActive }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(CardFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Headphones,
            contentDescription = null,
            tint = Pink,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Active now", style = MaterialTheme.typography.titleSmall, color = Cream)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active.isEmpty()) Ash else LightBlue)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (active.isEmpty()) "Nobody yet" else "${active.size} listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash
                )
            }
        }
        OverlappedAvatars(users = active, bitmaps = bitmaps)
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Ash
        )
    }
}

@Composable
private fun OverlappedAvatars(users: List<ActiveUser>, bitmaps: Map<String, Bitmap> = emptyMap()) {
    if (users.isEmpty()) return
    val shown = users.take(3)
    val extra = users.size - shown.size
    val avatar = 28.dp
    val step = 16.dp
    val count = shown.size + if (extra > 0) 1 else 0

    Box(modifier = Modifier.width(avatar + step * (count - 1)).height(avatar)) {
        shown.forEachIndexed { index, user ->
            UserAvatar(
                user = user,
                size = avatar,
                tint = avatarTint(index),
                photo = bitmaps[user.id]?.let { AvatarPhoto.Decoded(it) } ?: AvatarPhoto.None,
                modifier = Modifier.offset(x = step * index)
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = step * shown.size)
                    .size(avatar)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3A))
                    .border(2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+$extra", color = Cream, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ───────────────────────── Users sheet ─────────────────────────

@Composable
private fun ActiveUsersSheetContent(
    users: List<ActiveUser>,
    bitmaps: Map<String, Bitmap>,
    onUserClick: (ActiveUser) -> Unit,
    pickMode: Boolean = false
) {
    val activeCount = users.count { it.isActive }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active now",
                style = MaterialTheme.typography.titleLarge,
                color = Cream,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$activeCount listening",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash
            )
        }
        if (pickMode) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.poke_pick_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Ash
            )
        }
        Spacer(Modifier.height(12.dp))

        if (users.isEmpty()) {
            Text(
                text = "Nobody's listening right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        tint = avatarTint(users.indexOf(user)),
                        photo = bitmaps[user.id]?.let { AvatarPhoto.Decoded(it) } ?: AvatarPhoto.None,
                        onClick = { onUserClick(user) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    user: ActiveUser,
    tint: Color,
    onClick: () -> Unit,
    photo: AvatarPhoto = AvatarPhoto.None
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (user.isActive) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (user.isActive) 1f else 0.5f)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user = user, size = 44.dp, tint = tint, photo = photo)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                color = Cream
            )
            Text(
                text = if (user.isActive) {
                    user.nowPlaying ?: "Not playing"
                } else {
                    user.lastSeen?.let { "Last seen $it" } ?: "Offline"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (user.isActive) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = LightBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UserAvatar(
    user: ActiveUser,
    size: Dp,
    tint: Color,
    photo: AvatarPhoto = AvatarPhoto.None,
    modifier: Modifier = Modifier
) {
    // Legacy server URL still wins when present; otherwise the shared
    // component handles photo-over-letter for us.
    val resolved = when {
        photo != AvatarPhoto.None -> photo
        user.photoUri != null -> AvatarPhoto.Url(user.photoUri)
        else -> AvatarPhoto.None
    }
    Avatar(
        uid = user.id,
        name = user.name,
        size = size,
        modifier = modifier,
        photo = resolved,
        tint = tint,
        initialColor = OnTint
    )
}

private fun avatarTint(index: Int): Color = if (index % 2 == 0) Pink else LightBlue
