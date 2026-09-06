package durus.salafi.bangladesh.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
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
import android.app.Activity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import durus.salafi.bangladesh.model.SavedVideo
import durus.salafi.bangladesh.model.WatchRecord
import durus.salafi.bangladesh.ui.theme.AppTheme
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoDownloadTarget(
    val url: String,
    val title: String,
    val thumbnailUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionBottomSheet(
    target: VideoDownloadTarget,
    viewModel: DownloaderViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val isDownloaded = viewModel.isDownloaded(target.url)
    val activeDownloads by viewModel.activeDownloads
    val progressState = activeDownloads[target.url]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!target.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = target.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = target.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            if (progressState?.isDownloading == true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progressState.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (progressState.isPausedOffline) "Paused (Offline - Waiting for connection)"
                               else progressState.errorMessage ?: "Downloading... ${(progressState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isDownloaded) {
                Button(
                    onClick = {
                        viewModel.deleteDownload(target.url)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Downloaded Video")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.startDownload(target.url, target.title, target.thumbnailUrl)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download (720p)")
                }
            }

            if (progressState?.errorMessage != null && !progressState.isDownloading) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = progressState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(viewModel: DownloaderViewModel) {
    var selectedPlaylistUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var playingState by remember { mutableStateOf<PlayingTarget?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var videoDownloadTarget by remember { mutableStateOf<VideoDownloadTarget?>(null) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    val homeGridState = rememberLazyGridState()
    val playlistGridStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val playlistListStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }

    val loadedPlaylists by viewModel.loadedPlaylists
    val isLoadingSources by viewModel.isLoadingSources
    val watchRecords by viewModel.watchRecords
    val lastPlayedUrl = viewModel.lastPlayedUrl.value
    var localQuery by viewModel.localSearchQuery

    if (videoDownloadTarget != null) {
        VideoOptionBottomSheet(
            target = videoDownloadTarget!!,
            viewModel = viewModel,
            onDismiss = { videoDownloadTarget = null }
        )
    }

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

    if (showAbout) {
        BackHandler { showAbout = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("About") },
                    navigationIcon = {
                        IconButton(onClick = { showAbout = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            AboutScreen(viewModel = viewModel, onBack = null, contentPadding = innerPadding)
        }
        return
    }

    if (selectedPlaylistUrl != null) {
        BackHandler { selectedPlaylistUrl = null }
        val playlistUrl = selectedPlaylistUrl!!
        val playlistData = loadedPlaylists[playlistUrl]
        val gridState = playlistGridStates.getOrPut(playlistUrl) { LazyGridState() }
        val listState = playlistListStates.getOrPut(playlistUrl) { LazyListState() }

        PlaylistDetailScreen(
            playlistData = playlistData,
            watchRecords = watchRecords,
            viewModel = viewModel,
            gridState = gridState,
            listState = listState,
            lastPlayedUrl = lastPlayedUrl,
            onRefresh = { viewModel.refreshPlaylist(playlistUrl) },
            onBack = { selectedPlaylistUrl = null },
            onVideoSelected = { videoUrl, urls, index ->
                val video = playlistData?.videos?.getOrNull(index)
                if (video != null) {
                    val thumb = durus.salafi.bangladesh.util.getBestThumbnailUrl(video.thumbnails)
                    viewModel.addToHistory(
                        SavedVideo(
                            url = video.url,
                            title = video.name,
                            uploader = video.uploaderName ?: "",
                            thumbUrl = thumb ?: ""
                        )
                    )
                }
                playingState = PlayingTarget(videoUrl, urls, index)
            },
            onVideoLongClick = { item ->
                val thumb = durus.salafi.bangladesh.util.getBestThumbnailUrl(item.thumbnails)
                videoDownloadTarget = VideoDownloadTarget(item.url, item.name, thumb)
            }
        )
        return
    }

    if (isSearchActive) {
        BackHandler {
            isSearchActive = false
            localQuery = ""
        }
    } else if (selectedPlaylistUrl == null && playingState == null && !showAbout) {
        BackHandler {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Durūs", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadSourcesAndPlaylists(forceRefresh = true)
            pullToRefreshState.endRefresh()
        }
    }

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = localQuery,
                            onValueChange = { localQuery = it },
                            placeholder = { Text("Search...", style = MaterialTheme.typography.bodyLarge) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (localQuery.isNotEmpty()) {
                                            localQuery = ""
                                        } else {
                                            isSearchActive = false
                                        }
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 12.dp)
                                .height(50.dp)
                                .focusRequester(searchFocusRequester)
                        )
                    } else {
                        Text("Durūs", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {},
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Default.Info, contentDescription = "About")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val coroutineScope = rememberCoroutineScope()
            val showScrollToTop by remember { derivedStateOf { homeGridState.firstVisibleItemIndex > 3 } }
            val showResume = !isSearchActive && !lastPlayedUrl.isNullOrBlank()

            if (showScrollToTop || showResume) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showScrollToTop) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    homeGridState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                        }
                    }

                    if (showResume) {
                        ExtendedFloatingActionButton(
                            text = { Text("Resume") },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Resume Play") },
                            onClick = {
                                playingState = PlayingTarget(lastPlayedUrl!!, listOf(lastPlayedUrl), 0)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (isSearchActive) {
                val matchingPlaylists = remember(loadedPlaylists, localQuery) {
                    if (localQuery.isNotBlank()) {
                        val q = localQuery.trim().lowercase()
                        loadedPlaylists.values.filter { it.title.lowercase().contains(q) }
                    } else emptyList()
                }

                val matchingVideosWithPlaylistName = remember(loadedPlaylists, localQuery) {
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
                        Text("Type to search playlists and videos...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (matchingPlaylists.isEmpty() && matchingVideosWithPlaylistName.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching playlists or videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (matchingPlaylists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Playlists",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(matchingPlaylists) { pl ->
                                PlaylistTileCard(
                                    playlistData = pl,
                                    onClick = { selectedPlaylistUrl = pl.url }
                                )
                            }
                        }

                        if (matchingVideosWithPlaylistName.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Videos",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                            items(matchingVideosWithPlaylistName) { (item, playlistTitle) ->
                                val record = watchRecords[item.url]
                                val thumbUrl = durus.salafi.bangladesh.util.getBestThumbnailUrl(item.thumbnails)
                                VideoCardWithRecord(
                                    item = item,
                                    playlistBadge = playlistTitle,
                                    watchRecord = record,
                                    viewModel = viewModel,
                                    onClick = {
                                        viewModel.addToHistory(
                                            SavedVideo(
                                                url = item.url,
                                                title = item.name,
                                                uploader = item.uploaderName ?: "",
                                                thumbUrl = thumbUrl ?: ""
                                            )
                                        )
                                        playingState = PlayingTarget(item.url, listOf(item.url), 0)
                                    },
                                    onLongClick = {
                                        videoDownloadTarget = VideoDownloadTarget(item.url, item.name, thumbUrl)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                val isOffline = !viewModel.isOnline()
                val downloadedMap by viewModel.downloadedVideos

                if (isOffline) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "You are offline",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Showing your downloaded videos for offline viewing.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        if (downloadedMap.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No downloaded videos available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { viewModel.loadSourcesAndPlaylists(forceRefresh = true) }) {
                                        Text("Retry Connection")
                                    }
                                }
                            }
                        } else {
                            val downloadedList = downloadedMap.values.toList()
                            val downloadedUrls = downloadedList.map { it.url }

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 320.dp),
                                state = homeGridState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(downloadedList.size) { idx ->
                                    val record = downloadedList[idx]
                                    val streamItem = StreamInfoItem(0, record.url, record.title, org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM).apply {
                                        if (!record.thumbnailUrl.isNullOrBlank()) {
                                            thumbnails = listOf(org.schabi.newpipe.extractor.Image(record.thumbnailUrl, 0, 0, org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN))
                                        }
                                    }
                                    val watchRecord = watchRecords[record.url]

                                    VideoCardWithRecord(
                                        item = streamItem,
                                        watchRecord = watchRecord,
                                        viewModel = viewModel,
                                        onClick = {
                                            playingState = PlayingTarget(record.url, downloadedUrls, idx)
                                        },
                                        onLongClick = {
                                            videoDownloadTarget = VideoDownloadTarget(record.url, record.title, record.thumbnailUrl)
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (isLoadingSources && loadedPlaylists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (loadedPlaylists.isEmpty() || viewModel.sourcesErrorMessage.value != null) {
                    val errorMsg = viewModel.sourcesErrorMessage.value ?: "No playlists available"
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadSourcesAndPlaylists(forceRefresh = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                } else {
                    val pinnedSet by viewModel.pinnedPlaylists
                    val bottomSet by viewModel.completedBottomPlaylists
                    val allPlaylists = loadedPlaylists.values.toList()
                    val pinnedPlaylistsList = allPlaylists.filter { pinnedSet.contains(it.url) && !bottomSet.contains(it.url) }
                    val normalPlaylistsList = allPlaylists.filter { !pinnedSet.contains(it.url) && !bottomSet.contains(it.url) }
                    val bottomPlaylistsList = allPlaylists.filter { bottomSet.contains(it.url) }
                    val playlistsList = pinnedPlaylistsList + normalPlaylistsList + bottomPlaylistsList

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        state = homeGridState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlistsList) { pl ->
                            PlaylistTileCard(
                                playlistData = pl,
                                isPinned = pinnedSet.contains(pl.url),
                                onClick = { selectedPlaylistUrl = pl.url }
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Global persistent download progress bar at bottom of screen
            val activeDownloads by viewModel.activeDownloads
            val activeDownloadingItem = remember(activeDownloads) {
                activeDownloads.values.firstOrNull { it.isDownloading }
            }

            if (activeDownloadingItem != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeDownloadingItem.isPausedOffline) "Download Paused (Offline)"
                                       else activeDownloadingItem.errorMessage ?: "Downloading... ${(activeDownloadingItem.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeDownloadingItem.attempts > 1) {
                                Text(
                                    text = "Attempt ${activeDownloadingItem.attempts}/9",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { activeDownloadingItem.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
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
    isPinned: Boolean = false,
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

                if (isPinned) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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

fun toBengaliDigits(number: Long): String {
    val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return number.toString().map { ch ->
        if (ch in '0'..'9') bengaliDigits[ch - '0'] else ch
    }.joinToString("")
}

fun formatPlaylistDuration(videos: List<StreamInfoItem>): String {
    val totalSeconds = videos.sumOf { it.duration }
    if (totalSeconds <= 0) return ""

    val totalMinutes = totalSeconds / 60
    val totalHours = totalMinutes / 60

    return when {
        totalHours > 744 -> {
            val months = totalHours / 744
            val remDays = (totalHours % 744) / 24
            if (remDays > 0) {
                "${toBengaliDigits(months)} মাস ${toBengaliDigits(remDays)} দিন"
            } else {
                "${toBengaliDigits(months)} মাস"
            }
        }
        totalHours >= 24 -> {
            val days = totalHours / 24
            val remHours = totalHours % 24
            if (remHours > 0) {
                "${toBengaliDigits(days)} দিন ${toBengaliDigits(remHours)} ঘন্ট"
            } else {
                "${toBengaliDigits(days)} দিন"
            }
        }
        else -> {
            val hours = totalMinutes / 60
            val remMinutes = totalMinutes % 60
            if (hours > 0 && remMinutes > 0) {
                "${toBengaliDigits(hours)} ঘন্ট ${toBengaliDigits(remMinutes)} মিনিট"
            } else if (hours > 0) {
                "${toBengaliDigits(hours)} ঘন্ট"
            } else {
                "${toBengaliDigits(remMinutes)} মিনিট"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistData: PlaylistData?,
    watchRecords: Map<String, WatchRecord>,
    viewModel: DownloaderViewModel,
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState(),
    lastPlayedUrl: String? = null,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onVideoSelected: (String, List<String>, Int) -> Unit,
    onVideoLongClick: ((StreamInfoItem) -> Unit)? = null
) {
    val rawVideos = playlistData?.videos ?: emptyList()
    val pinnedSet by viewModel.pinnedPlaylists
    val reversedSet by viewModel.reversedPlaylists

    val isPinned = playlistData?.url?.let { pinnedSet.contains(it) } == true
    val isReversed = playlistData?.url?.let { reversedSet.contains(it) } == true

    val videos = remember(rawVideos, isReversed) { if (isReversed) rawVideos.reversed() else rawVideos }
    val videoUrls = remember(videos) { videos.map { it.url } }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        playlistData?.title ?: "Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            val coroutineScope = rememberCoroutineScope()
            val showScrollToTop by remember {
                derivedStateOf {
                    if (isLandscape) gridState.firstVisibleItemIndex > 3
                    else listState.firstVisibleItemIndex > 3
                }
            }
            val showResume = !lastPlayedUrl.isNullOrBlank()

            if (showScrollToTop || showResume) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showScrollToTop) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (isLandscape) gridState.animateScrollToItem(0)
                                    else listState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                        }
                    }

                    if (showResume) {
                        ExtendedFloatingActionButton(
                            text = { Text("Resume") },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Resume Play") },
                            onClick = {
                                val index = videoUrls.indexOf(lastPlayedUrl).coerceAtLeast(0)
                                onVideoSelected(lastPlayedUrl!!, if (videoUrls.isNotEmpty()) videoUrls else listOf(lastPlayedUrl), index)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
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
                    val formattedDuration = remember(videos) { formatPlaylistDuration(videos) }
                    val headerCountText = if (formattedDuration.isNotBlank()) {
                        "${videos.size} Videos • $formattedDuration"
                    } else {
                        "${videos.size} Videos"
                    }

                    // Header Bar with Icon Play All, Filter/Reverse 🔃, and Pin/Unpin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = headerCountText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 1. Play All button (Icon only)
                            IconButton(
                                onClick = {
                                    if (videos.isNotEmpty()) {
                                        onVideoSelected(videos[0].url, videoUrls, 0)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                            }

                            // 2. Filter button (🔃 icon with no text, toggles reversed order)
                            IconButton(
                                onClick = {
                                    playlistData?.url?.let { viewModel.togglePlaylistOrder(it) }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Autorenew,
                                    contentDescription = "Filter / Reverse",
                                    tint = if (isReversed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 3. Pin button (Icon only, matching other buttons)
                            IconButton(
                                onClick = {
                                    playlistData?.url?.let { viewModel.togglePinPlaylist(it) }
                                }
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pin / Unpin",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (isLandscape) {
                        // Landscape mode: Grid view fitting 2 videos in a line
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
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
                                    viewModel = viewModel,
                                    onClick = { onVideoSelected(item.url, videoUrls, index) },
                                    onLongClick = { onVideoLongClick?.invoke(item) }
                                )
                            }
                        }
                    } else {
                        // Portrait mode: Full-width edge-to-edge thumbnails like YouTube app UI
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(videos.size) { index ->
                                val item = videos[index]
                                val record = watchRecords[item.url]
                                YouTubeStyleVideoItem(
                                    item = item,
                                    watchRecord = record,
                                    viewModel = viewModel,
                                    onClick = { onVideoSelected(item.url, videoUrls, index) },
                                    onLongClick = { onVideoLongClick?.invoke(item) }
                                )
                            }
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeStyleVideoItem(
    item: StreamInfoItem,
    watchRecord: WatchRecord?,
    viewModel: DownloaderViewModel? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val thumbUrl = durus.salafi.bangladesh.util.getBestThumbnailUrl(item.thumbnails)
            AsyncImage(
                model = thumbUrl ?: "",
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

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val isDownloaded = viewModel?.isDownloaded(item.url) == true
            val hasWatched = watchRecord != null && watchRecord.durationMs > 0

            if (isDownloaded || hasWatched) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDownloaded) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Downloaded",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (hasWatched) {
                        val percent = ((watchRecord!!.positionMs.toFloat() / watchRecord.durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = if (percent >= 98) "Watched" else "$percent% watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCardWithRecord(
    item: StreamInfoItem,
    playlistBadge: String? = null,
    watchRecord: WatchRecord?,
    viewModel: DownloaderViewModel? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val bestThumb = durus.salafi.bangladesh.util.getBestThumbnailUrl(item.thumbnails)
                AsyncImage(
                    model = bestThumb ?: "",
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

                val isDownloaded = viewModel?.isDownloaded(item.url) == true
                val hasWatched = watchRecord != null && watchRecord.durationMs > 0

                if (isDownloaded || hasWatched) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isDownloaded) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Downloaded",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (hasWatched) {
                            val percent = ((watchRecord!!.positionMs.toFloat() / watchRecord.durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
                            Text(
                                text = if (percent >= 98) "Watched" else "$percent% watched",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
            "Storage" -> StorageScreen(viewModel, onBack = { currentScreen = "Main" }, contentPadding = contentPadding)
            "About" -> AboutScreen(viewModel = viewModel, onBack = { currentScreen = "Main" }, contentPadding = contentPadding)
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
                icon = Icons.Default.FolderDelete,
                iconColor = Color(0xFF8B5A4C),
                title = "Storage & Downloads",
                subtitle = "Clear downloaded videos & cache",
                onClick = { onNavigate("Storage") }
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

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        val gb = mb / 1024
        "%.2f GB".format(gb)
    } else {
        "%.1f MB".format(mb)
    }
}

@Composable
fun StorageScreen(viewModel: DownloaderViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    var downloadedSize by remember { mutableLongStateOf(viewModel.getDownloadedVideosSize()) }
    var imageCacheSize by remember { mutableLongStateOf(viewModel.getImageCacheSize()) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }

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
            Text("Storage & Downloads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App Storage Usage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Downloaded Videos:", style = MaterialTheme.typography.bodyLarge)
                    Text(formatByteSize(downloadedSize), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Thumbnail Image Cache:", style = MaterialTheme.typography.bodyLarge)
                    Text(formatByteSize(imageCacheSize), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Cache & Storage:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(formatByteSize(downloadedSize + imageCacheSize), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Button(
            onClick = { showClearDownloadsDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Clear Downloads", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = {
                viewModel.clearImageCache()
                imageCacheSize = viewModel.getImageCacheSize()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Clear Thumbnail Cache")
        }
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text("Clear All Downloads", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all downloaded videos at once? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDownloads()
                        downloadedSize = viewModel.getDownloadedVideosSize()
                        showClearDownloadsDialog = false
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
fun AboutScreen(viewModel: DownloaderViewModel? = null, onBack: (() -> Unit)? = null, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var downloadedSize by remember { mutableLongStateOf(viewModel?.getDownloadedVideosSize() ?: 0L) }
    var imageCacheSize by remember { mutableLongStateOf(viewModel?.getImageCacheSize() ?: 0L) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "1.0.0"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onBack != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.width(8.dp))
                Text("About", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "Durūs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A distraction-free, ad-free Islamic video learning app designed to facilitate focused study of curated playlists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel != null) {
                    val loadedMap by viewModel.loadedPlaylists
                    val totalPlaylists = loadedMap.size
                    val totalVideos = loadedMap.values.sumOf { it.videos.size }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Library Statistics",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$totalPlaylists Playlists • $totalVideos Videos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Messenger: https://m.me/abdullahbariasif
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://m.me/abdullahbariasif"))
                            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Messenger",
                            tint = Color(0xFF0084FF)
                        )
                    }

                    // WhatsApp: https://wa.me/abdullahbariasif
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/abdullahbariasif"))
                            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = "WhatsApp",
                            tint = Color(0xFF25D366)
                        )
                    }

                    // E-mail: mailto:contact@abdullah.ami.bd
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:contact@abdullah.ami.bd"))
                            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "E-mail",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Phone: tel:+8809638250306
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+8809638250306"))
                            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Phone",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "App Version",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Creator Credit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Created by Abdullah Bari Asif",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel != null) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Cache & Downloads (${formatByteSize(downloadedSize + imageCacheSize)})", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val url = "https://wa.link/n7blpl"
                        val whatsappIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                            setPackage("com.whatsapp")
                        }
                        try {
                            context.startActivity(whatsappIntent)
                        } catch (e: Exception) {
                            try {
                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(fallbackIntent)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Submit New playlists",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showClearCacheDialog && viewModel != null) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache & Downloads", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all downloaded videos and cached thumbnails at once?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDownloads()
                        viewModel.clearImageCache()
                        downloadedSize = viewModel.getDownloadedVideosSize()
                        imageCacheSize = viewModel.getImageCacheSize()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
