package com.jinki.app.ui.user

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
    val lastSeen: String? = null
)

sealed interface SyncState {
    data object Idle : SyncState
    data object Checking : SyncState
    data object Success : SyncState
    data object WrongPassword : SyncState
    data class Error(val message: String) : SyncState
}

/*
 * UserViewModel additions this screen expects:
 *
 *   val activeUsers: StateFlow<List<ActiveUser>>
 *   val syncedWith: StateFlow<ActiveUser?>
 *   val syncState: StateFlow<SyncState>
 *   fun syncWithUser(userId: String, password: String)   // verify on your backend, never in the app
 *   fun stopSync()
 *   fun resetSyncState()
 *
 * Removed (no longer used here): messages, draft, onDraftChange, sendMessage.
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
    val partnerSeconds by viewModel.partnerSeconds.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val syncedWith by viewModel.syncedWith.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    var showTimer by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var syncTarget by remember { mutableStateOf<ActiveUser?>(null) }
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

    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success) {
            syncTarget = null
            showUsers = false
            viewModel.resetSyncState()
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
            syncedWith?.let { user ->
                SyncedBanner(user = user, onStop = { viewModel.stopSync() })
            }

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

            ActiveUsersCard(
                users = activeUsers,
                onClick = { showUsers = true },
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
            onDismissRequest = { showUsers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetFill
        ) {
            ActiveUsersSheetContent(
                users = activeUsers,
                onUserClick = { user ->
                    viewModel.resetSyncState()
                    syncTarget = user
                }
            )
        }
    }

    syncTarget?.let { user ->
        SyncPasswordDialog(
            user = user,
            state = syncState,
            onEdit = { if (syncState !is SyncState.Checking) viewModel.resetSyncState() },
            onConfirm = { password -> viewModel.syncWithUser(user.id, password) },
            onDismiss = {
                viewModel.resetSyncState()
                syncTarget = null
            }
        )
    }
}

// ───────────────────────── Hero ─────────────────────────

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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "",
                fontFamily = FontFamily.Serif,
                fontSize = 120.sp,
                color = Pink.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center)
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
private fun LoveCard(partnerSeconds: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(CardFill)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Love?",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            color = Pink
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "His listening today",
                style = MaterialTheme.typography.labelMedium,
                color = Ash
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
        OverlappedAvatars(users = active)
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Ash
        )
    }
}

@Composable
private fun OverlappedAvatars(users: List<ActiveUser>) {
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

@Composable
private fun SyncedBanner(user: ActiveUser, onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(LightBlue.copy(alpha = 0.16f))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Sync,
            contentDescription = null,
            tint = LightBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Synced with ${user.name}",
                style = MaterialTheme.typography.titleSmall,
                color = Cream
            )
            Text(
                text = user.nowPlaying ?: "Listening together",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onStop) { Text("Stop sync", color = Pink) }
    }
}

// ───────────────────────── Users sheet ─────────────────────────

@Composable
private fun ActiveUsersSheetContent(
    users: List<ActiveUser>,
    onUserClick: (ActiveUser) -> Unit
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
                        onClick = { onUserClick(user) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: ActiveUser, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (user.isActive) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (user.isActive) 1f else 0.5f)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user = user, size = 44.dp, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                color = Cream
            )
            Text(
                text = if (user.isActive) {
                    user.nowPlaying ?: "Listening"
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
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pink)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Sync",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = OnTint
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(
    user: ActiveUser,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint)
            .border(2.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (user.photoUri != null) {
            AsyncImage(
                model = user.photoUri,
                contentDescription = user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = user.name.firstOrNull()?.uppercase() ?: "",
                color = OnTint,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

private fun avatarTint(index: Int): Color = if (index % 2 == 0) Pink else LightBlue

// ───────────────────────── Password dialog ─────────────────────────

@Composable
private fun SyncPasswordDialog(
    user: ActiveUser,
    state: SyncState,
    onEdit: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember(user.id) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val checking = state is SyncState.Checking
    val failed = state is SyncState.WrongPassword || state is SyncState.Error

    val errorText = when (state) {
        is SyncState.WrongPassword -> "Wrong password. Try again."
        is SyncState.Error -> state.message
        else -> null
    }

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        containerColor = DialogFill,
        title = {
            Text(
                text = "Sync with ${user.name}",
                color = Cream,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter ${user.name}'s password",
                    color = Ash,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (failed) onEdit()
                    },
                    singleLine = true,
                    enabled = !checking,
                    isError = failed,
                    placeholder = { Text("Password", color = Ash) },
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (password.isNotEmpty() && !checking) onConfirm(password) }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) Icons.Outlined.VisibilityOff
                                else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                                tint = Ash
                            )
                        }
                    },
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Cream,
                        unfocusedTextColor = Cream,
                        disabledTextColor = Ash,
                        cursorColor = Pink,
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = Ash,
                        errorBorderColor = Pink,
                        errorTextColor = Cream,
                        errorCursorColor = Pink,
                        errorSupportingTextColor = Pink
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (checking) {
                CircularProgressIndicator(
                    color = Pink,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(20.dp)
                )
            } else {
                TextButton(
                    onClick = { onConfirm(password) },
                    enabled = password.isNotEmpty()
                ) { Text("Sync", color = if (password.isNotEmpty()) Pink else Ash) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checking) {
                Text("Cancel", color = Ash)
            }
        }
    )
}
