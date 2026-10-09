package com.jinki.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.jinki.app.AppContainer
import com.jinki.app.BuildConfig
import com.jinki.app.R
import com.jinki.app.data.artwork.trackThumbnailCandidates
import com.jinki.app.ui.library.LibraryScreen
import com.jinki.app.ui.library.LibraryViewModel
import com.jinki.app.ui.nowplaying.NowPlayingRoute
import com.jinki.app.ui.nowplaying.NowPlayingViewModel
import com.jinki.app.ui.nowplaying.UsernameOverlay
import com.jinki.app.ui.search.SearchScreen
import com.jinki.app.ui.search.SearchViewModel
import com.jinki.app.ui.user.UserScreen
import com.jinki.app.ui.user.NameSaveState
import com.jinki.app.ui.user.UserViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch


enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    @DrawableRes val iconRes: Int? = null
) {
    Home("home", R.string.nav_home, Icons.Outlined.Public),
    Search("search", R.string.nav_search, Icons.Outlined.Search),
    Library("library", R.string.nav_library, Icons.Outlined.MusicNote),
    User("user", R.string.nav_user, Icons.Outlined.Person, R.drawable.ic_nav_user)
}


private val AppBackdrop = Color(0xFF000000)


val LocalPlayerContentPadding = compositionLocalOf { PaddingValues() }


private const val TAB_TRANSITION_MS = 280


private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = Destination.entries.indexOfFirst { it.route == initialState.destination.route }
    val to = Destination.entries.indexOfFirst { it.route == targetState.destination.route }
    return if (to >= from) 1 else -1
}

private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    val direction = tabDirection()
    slideInHorizontally(
        animationSpec = tween(TAB_TRANSITION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth / 6 * direction }
    ) + fadeIn(tween(TAB_TRANSITION_MS, easing = FastOutSlowInEasing))
}

private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    val direction = tabDirection()
    slideOutHorizontally(
        animationSpec = tween(TAB_TRANSITION_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> -fullWidth / 6 * direction }
    ) + fadeOut(tween(TAB_TRANSITION_MS / 2))
}

@Composable
fun JinkaApp(container: AppContainer, openYou: SharedFlow<Unit>? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources

    // Notification tap (PendingIntent extra open=user): land on the You tab.
    LaunchedEffect(openYou) {
        openYou?.collect {
            navController.navigate(Destination.User.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // One shared instance for the User tab and the username overlay below,
    // so both see the same name state.
    val userVm: UserViewModel = viewModel(factory = UserViewModel.Factory(container))
    val needsName by userVm.needsName.collectAsStateWithLifecycle()
    val nameSaveState by userVm.saveState.collectAsStateWithLifecycle()
    val nameStatus by userVm.statusText.collectAsStateWithLifecycle()
    val currentName by userVm.name.collectAsStateWithLifecycle()

    
    
    
    
    LaunchedEffect(container) {
        container.connection.playbackErrors.collect { failure ->
            scope.launch {
                val message = failure.skippedTitle
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        resources.getString(R.string.playback_error_skipped, it, failure.message)
                    }
                    ?: failure.message
                // TEMP DEBUG AID — remove after diagnosing the "no track" issue.
                val debugMessage =
                    if (BuildConfig.DEBUG && failure.errorClass != null) "$message [${failure.errorClass}]"
                    else message
                snackbar.showSnackbar(debugMessage)
            }
        }
    }

    
    
    
    Box(modifier = Modifier.fillMaxSize().background(AppBackdrop)) {
        Scaffold(
            
            
            containerColor = Color.Transparent,
            
            
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    
                    
                    
                    AnimatedVisibility(
                        visible = currentRoute != Destination.Home.route,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        MiniPlayer(
                            container = container,
                            onOpenPlayer = {
                                navController.navigate(Destination.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    FloatingNavBar(
                        currentRoute = currentRoute,
                        onSelect = { destination ->
                            if (currentRoute == destination.route) return@FloatingNavBar
                            navController.navigate(destination.route) {
                                
                                
                                
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            
            val bottomBarPadding = innerPadding.calculateBottomPadding()
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabEnter,
                popExitTransition = tabExit
            ) {
                composable(Destination.Home.route) {
                    val vm: NowPlayingViewModel =
                        viewModel(factory = NowPlayingViewModel.Factory(container))
                    
                    
                    
                    
                    CompositionLocalProvider(
                        LocalPlayerContentPadding provides PaddingValues(
                            top = statusBarPadding,
                            bottom = bottomBarPadding
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            NowPlayingRoute(vm, onCollapse = { navController.popBackStack() })
                        }
                    }
                }
                composable(Destination.Search.route) {
                    val vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory(container))
                    Box(modifier = Modifier.fillMaxSize().padding(top = statusBarPadding, bottom = bottomBarPadding)) {
                        SearchScreen(vm, snackbar)
                    }
                }
                composable(Destination.Library.route) {
                    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(container))
                    Box(modifier = Modifier.fillMaxSize().padding(top = statusBarPadding, bottom = bottomBarPadding)) {
                        LibraryScreen(vm, snackbar)
                    }
                }
                composable(Destination.User.route) {
                    Box(modifier = Modifier.fillMaxSize().padding(top = statusBarPadding, bottom = bottomBarPadding)) {
                        UserScreen(userVm, snackbar)
                    }
                }
            }
        }
        // Drawn last so it covers everything, only while no confirmed name
        // is saved. Stays up through offline stretches until the server
        // acknowledges the name.
        if (needsName) {
            UsernameOverlay(
                onSubmit = userVm::saveUserName,
                isSaving = nameSaveState is NameSaveState.Saving,
                errorText = (nameSaveState as? NameSaveState.Error)?.message,
                statusText = nameStatus,
                initialName = currentName.takeIf { it.isNotBlank() }
            )
        }
    }
}


@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = Color(0x80191B37),
        shadowElevation = 8.dp,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Destination.entries.forEach { destination ->
                FloatingNavItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelect(destination) }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(destination.labelRes)
    val background by animateColorAsState(
        targetValue = if (selected) Color(0x80191919) else Color.Transparent,
        label = "navItemBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFF69CC7) else Color.White,
        label = "navItemContent"
    )

    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        
        
        val customIcon = destination.iconRes
        if (customIcon != null) {
            Icon(
                painter = painterResource(customIcon),
                
                contentDescription = if (selected) null else label,
                tint = contentColor
            )
        } else {
            Icon(
                imageVector = destination.icon,
                
                contentDescription = if (selected) null else label,
                tint = contentColor
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}


@Composable
private fun MiniPlayer(container: AppContainer, onOpenPlayer: () -> Unit) {
    val player by container.connection.state.collectAsStateWithLifecycle()
    val track = player.track ?: return
    val lastResults by container.lastSearchResults.collectAsStateWithLifecycle()
    val candidates = remember(track, lastResults) {
        trackThumbnailCandidates(track, lastResults)
    }

    val progress = if (player.durationMs > 0) {
        (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        shape = RoundedCornerShape(15.dp),
        color = MiniPlayerBackground,
        shadowElevation = 8.dp,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPlayer)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FallbackArtwork(candidates, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = track.title.ifBlank { stringResource(R.string.unknown_title) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { container.connection.toggle() }) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (player.isPlaying) R.string.action_pause else R.string.action_play
                        ),
                        tint = Color.White
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(MiniPlayerProgress)
            )
        }
    }
}

private val MiniPlayerProgress = Color(0xFFB5761F)


private val MiniPlayerBackground = Color(0xFF191B37)


private val MiniPlayerArtworkBackground = Color(0xFF2A2C4A)


@Composable
private fun FallbackArtwork(candidates: List<String>, modifier: Modifier = Modifier) {
    var index by remember(candidates) { mutableIntStateOf(0) }
    val model = candidates.getOrNull(index)
    Box(modifier = modifier.background(MiniPlayerArtworkBackground), contentAlignment = Alignment.Center) {
        if (model == null) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
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
