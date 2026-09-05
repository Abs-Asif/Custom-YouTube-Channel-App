package durus.salafi.bangladesh.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import durus.salafi.bangladesh.model.SavedVideo
import durus.salafi.bangladesh.model.WatchRecord
import durus.salafi.bangladesh.ui.theme.AppTheme
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(viewModel: DownloaderViewModel) {
    var selectedPlaylistUrl by remember { mutableStateOf<String?>(null) }
    var playingState by remember { mutableStateOf<PlayingTarget?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    val loadedPlaylists by viewModel.loadedPlaylists
    val isLoadingSources by viewModel.isLoadingSources
    val watchRecords by viewModel.watchRecords
    var localQuery by viewModel.localSearchQuery

    if (playingState != null) {
        PlayerScreen(
            url = playingState!!.url,
            playlistUrls = playingState!!.playlistUrls,
            initialIndex = playingState!!.index,
            viewModel = viewModel,
            onBack = { playingState = null }
        )
        return
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            SettingsTab(viewModel = viewModel, contentPadding = innerPadding)
        }
        return
    }

    if (selectedPlaylistUrl != null) {
        BackHandler { selectedPlaylistUrl = null }
        val playlistData = loadedPlaylists[selectedPlaylistUrl!!]
        PlaylistDetailScreen(
            playlistData = playlistData,
            watchRecords = watchRecords,
            onBack = { selectedPlaylistUrl = null },
            onVideoSelected = { videoUrl, urls, index ->
                val video = playlistData?.videos?.getOrNull(index)
                if (video != null) {
                    viewModel.addToHistory(
                        SavedVideo(
                            url = video.url,
                            title = video.name,
                            uploader = video.uploaderName ?: "",
                            thumbUrl = video.thumbnails?.firstOrNull()?.url ?: ""
                        )
                    )
                }
                playingState = PlayingTarget(videoUrl, urls, index)
            }
        )
        return
    }

    if (isSearchActive) {
        BackHandler {
            isSearchActive = false
            localQuery = ""
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadSourcesAndPlaylists(forceRefresh = true)
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = localQuery,
                            onValueChange = { localQuery = it },
                            placeholder = { Text("Search video titles...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (localQuery.isNotEmpty()) {
                                    IconButton(onClick = { localQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("Durūs", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            localQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isSearchActive) {
                // Search across all loaded playlist videos
                val allVideosWithPlaylistName = remember(loadedPlaylists, localQuery) {
                    val list = mutableListOf<Pair<StreamInfoItem, String>>()
                    if (localQuery.isNotBlank()) {
                        val q = localQuery.trim().lowercase()
                        loadedPlaylists.values.forEach { pl ->
                            pl.videos.forEach { v ->
                                if (v.name.lowercase().contains(q)) {
                                    list.add(Pair(v, pl.title))
                                }
                            }
                        }
                    }
                    list
                }

                if (localQuery.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type to search video titles across playlists", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (allVideosWithPlaylistName.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(allVideosWithPlaylistName) { (item, playlistTitle) ->
                            val record = watchRecords[item.url]
                            VideoCardWithRecord(
                                item = item,
                                playlistBadge = playlistTitle,
                                watchRecord = record,
                                onClick = {
                                    viewModel.addToHistory(
                                        SavedVideo(
                                            url = item.url,
                                            title = item.name,
                                            uploader = item.uploaderName ?: "",
                                            thumbUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                                        )
                                    )
                                    playingState = PlayingTarget(item.url, listOf(item.url), 0)
                                }
                            )
                        }
                    }
                }
            } else {
                // Home Playlist Tiles Grid
                if (isLoadingSources && loadedPlaylists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (loadedPlaylists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No playlists available")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadSourcesAndPlaylists(forceRefresh = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                } else {
                    val playlistsList = loadedPlaylists.values.toList()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlistsList) { pl ->
                            PlaylistTileCard(
                                playlistData = pl,
                                onClick = { selectedPlaylistUrl = pl.url }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class PlayingTarget(
    val url: String,
    val playlistUrls: List<String>,
    val index: Int
)

@Composable
fun PlaylistTileCard(
    playlistData: PlaylistData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (playlistData.thumbnailUrl != null) {
                    AsyncImage(
                        model = playlistData.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (playlistData.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (playlistData.videoCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${playlistData.videoCount} videos",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = playlistData.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlistData.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = playlistData.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistData: PlaylistData?,
    watchRecords: Map<String, WatchRecord>,
    onBack: () -> Unit,
    onVideoSelected: (String, List<String>, Int) -> Unit
) {
    val videos = playlistData?.videos ?: emptyList()
    val videoUrls = remember(videos) { videos.map { it.url } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        playlistData?.title ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (playlistData == null || playlistData.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (videos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No videos found in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar with "Play All" Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${videos.size} Videos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = {
                                if (videos.isNotEmpty()) {
                                    onVideoSelected(videos[0].url, videoUrls, 0)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play All")
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(videos.size) { index ->
                            val item = videos[index]
                            val record = watchRecords[item.url]
                            VideoCardWithRecord(
                                item = item,
                                watchRecord = record,
                                onClick = { onVideoSelected(item.url, videoUrls, index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCardWithRecord(
    item: StreamInfoItem,
    playlistBadge: String? = null,
    watchRecord: WatchRecord?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = item.thumbnails?.firstOrNull()?.url ?: "",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (item.duration > 0) {
                    val minutes = item.duration / 60
                    val seconds = item.duration % 60
                    val durationText = "%d:%02d".format(minutes, seconds)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = durationText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Visual watch progress bar
                if (watchRecord != null && watchRecord.durationMs > 0) {
                    val progress = (watchRecord.positionMs.toFloat() / watchRecord.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                if (playlistBadge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = playlistBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (watchRecord != null && watchRecord.durationMs > 0) {
                    val percent = ((watchRecord.positionMs.toFloat() / watchRecord.durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (percent >= 90) "Watched" else "$percent% watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(viewModel: DownloaderViewModel, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    var currentScreen by remember { mutableStateOf("Main") }

    BackHandler(enabled = currentScreen != "Main") {
        currentScreen = "Main"
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState != "Main") {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) togetherWith slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) togetherWith slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        },
        label = "settings_transition"
    ) { screen ->
        when (screen) {
            "Main" -> SettingsMainList(onNavigate = { currentScreen = it }, contentPadding = contentPadding)
            "Customisation" -> CustomisationScreen(viewModel, onBack = { currentScreen = "Main" }, contentPadding = contentPadding)
            "About" -> AboutScreen(onBack = { currentScreen = "Main" }, contentPadding = contentPadding)
        }
    }
}

@Composable
fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    topRadius: androidx.compose.ui.unit.Dp = 0.dp,
    bottomRadius: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)
    val currentTop by animateDpAsState(if (isPressed) 24.dp else topRadius)
    val currentBottom by animateDpAsState(if (isPressed) 24.dp else bottomRadius)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(
                topStart = currentTop, topEnd = currentTop,
                bottomStart = currentBottom, bottomEnd = currentBottom
            ))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SettingsMainList(onNavigate: (String) -> Unit, contentPadding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SettingsListItem(
                icon = Icons.Default.Palette,
                iconColor = Color(0xFF88637B),
                title = "Customisation",
                subtitle = "Themes & App Appearance",
                topRadius = 24.dp,
                onClick = { onNavigate("Customisation") }
            )
            SettingsListItem(
                icon = Icons.Default.Info,
                iconColor = Color(0xFF4C6B8B),
                title = "About",
                subtitle = "App info & Credits",
                onClick = { onNavigate("About") }
            )

            val packageInfo = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) {
                    null
                }
            }
            val versionName = packageInfo?.versionName ?: "1.0.0"

            SettingsListItem(
                icon = Icons.Default.Info,
                iconColor = Color(0xFF6B8B4C),
                title = "App Info",
                subtitle = "Version $versionName",
                bottomRadius = 24.dp,
                onClick = {}
            )
        }
    }
}

@Composable
fun CustomisationScreen(viewModel: DownloaderViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    val themeMode = viewModel.themeMode.intValue
    val selectedTheme = viewModel.selectedTheme.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(8.dp))
            Text("Customisation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Theme Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == 0, onClick = { viewModel.setThemeMode(0) }, label = { Text("System") })
                    FilterChip(selected = themeMode == 1, onClick = { viewModel.setThemeMode(1) }, label = { Text("Light") })
                    FilterChip(selected = themeMode == 2, onClick = { viewModel.setThemeMode(2) }, label = { Text("Dark") })
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTheme.values().forEach { theme ->
                        FilterChip(
                            selected = selectedTheme == theme,
                            onClick = { viewModel.setAppTheme(theme) },
                            label = { Text(theme.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(8.dp))
            Text("About", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "Durūs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A distraction-free, ad-free Islamic video learning app designed to facilitate focused study of curated playlists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
