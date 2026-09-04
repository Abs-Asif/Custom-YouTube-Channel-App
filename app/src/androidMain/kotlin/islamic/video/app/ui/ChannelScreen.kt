package islamic.video.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

object ChannelCache {
    data class ChannelData(
        val name: String,
        val avatarUrl: String?,
        val subscriberCount: String,
        val videos: List<StreamInfoItem>,
        val playlists: List<PlaylistInfoItem>
    )

    private val channelDataMap = ConcurrentHashMap<String, ChannelData>()
    private val playlistVideosMap = ConcurrentHashMap<String, List<StreamInfoItem>>()

    fun getChannel(url: String): ChannelData? = channelDataMap[url]
    fun putChannel(url: String, data: ChannelData) { channelDataMap[url] = data }
    fun clearChannel(url: String) { channelDataMap.remove(url) }

    fun getPlaylistVideos(url: String): List<StreamInfoItem>? = playlistVideosMap[url]
    fun putPlaylistVideos(url: String, videos: List<StreamInfoItem>) { playlistVideosMap[url] = videos }
    fun clearPlaylistVideos(url: String) { playlistVideosMap.remove(url) }
}

fun formatSubscriberCount(countStr: String): String {
    val count = countStr.toLongOrNull() ?: return countStr
    if (count <= 0) return ""
    return when {
        count >= 1_000_000_000 -> {
            val v = count / 1_000_000_000.0
            if (v % 1.0 == 0.0) "${v.toLong()}B" else String.format(Locale.US, "%.1fB", v)
        }
        count >= 1_000_000 -> {
            val v = count / 1_000_000.0
            if (v % 1.0 == 0.0) "${v.toLong()}M" else String.format(Locale.US, "%.1fM", v)
        }
        count >= 1_000 -> {
            val v = count / 1_000.0
            if (v % 1.0 == 0.0) "${v.toLong()}K" else String.format(Locale.US, "%.1fK", v)
        }
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    url: String,
    onBack: (() -> Unit)? = null,
    onVideoSelected: (String, List<String>, Int) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var channelName by remember { mutableStateOf("") }
    var channelAvatar by remember { mutableStateOf<String?>(null) }
    var subscriberCount by remember { mutableStateOf("") }

    var selectedInnerTab by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Playlists

    var videos by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistInfoItem>>(emptyList()) }

    // For viewing a selected playlist's videos inside ChannelScreen
    var activePlaylistUrl by remember { mutableStateOf<String?>(null) }
    var activePlaylistName by remember { mutableStateOf<String?>(null) }
    var playlistVideos by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    var isPlaylistVideosLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (activePlaylistUrl != null) {
        BackHandler {
            activePlaylistUrl = null
        }
    } else if (onBack != null) {
        BackHandler {
            onBack()
        }
    }

    val fetchChannelData: suspend (Boolean) -> Unit = { forceRefresh ->
        if (forceRefresh) {
            ChannelCache.clearChannel(url)
        } else {
            val cached = ChannelCache.getChannel(url)
            if (cached != null) {
                channelName = cached.name
                channelAvatar = cached.avatarUrl
                subscriberCount = cached.subscriberCount
                videos = cached.videos
                playlists = cached.playlists
                isLoading = false
            }
        }

        if (forceRefresh || ChannelCache.getChannel(url) == null) {
            try {
                val channelInfo = ChannelInfo.getInfo(url)
                val tabs = channelInfo.tabs

                var fetchedVideos = emptyList<StreamInfoItem>()
                var fetchedPlaylists = mutableListOf<PlaylistInfoItem>()

                // Fetch videos tab
                val videoTabLink = tabs.firstOrNull {
                    val id = try { it.id } catch(e: Exception) { "" }
                    val tabUrl = try { it.url } catch(e: Exception) { "" }
                    id.lowercase().contains("video") || tabUrl.lowercase().contains("video")
                } ?: tabs.firstOrNull()

                if (videoTabLink != null) {
                    try {
                        val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, videoTabLink)
                        fetchedVideos = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Fetch playlists across tabs
                for (tab in tabs) {
                    try {
                        val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, tab)
                        val items = tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>()
                        if (items.isNotEmpty()) {
                            fetchedPlaylists.addAll(items)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val cName = channelInfo.name ?: "Channel"
                val cAvatar = channelInfo.avatars?.firstOrNull()?.url
                val cSubCount = channelInfo.subscriberCount.toString()

                ChannelCache.putChannel(
                    url,
                    ChannelCache.ChannelData(cName, cAvatar, cSubCount, fetchedVideos, fetchedPlaylists)
                )

                withContext(Dispatchers.Main) {
                    channelName = cName
                    channelAvatar = cAvatar
                    subscriberCount = cSubCount
                    videos = fetchedVideos
                    playlists = fetchedPlaylists
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(url) {
        scope.launch(Dispatchers.IO) {
            fetchChannelData(false)
        }
    }

    fun loadPlaylist(plUrl: String, plName: String, forceRefresh: Boolean = false) {
        activePlaylistUrl = plUrl
        activePlaylistName = plName

        if (forceRefresh) {
            ChannelCache.clearPlaylistVideos(plUrl)
        } else {
            val cachedPlVideos = ChannelCache.getPlaylistVideos(plUrl)
            if (cachedPlVideos != null) {
                playlistVideos = cachedPlVideos
                isPlaylistVideosLoading = false
                return
            }
        }

        isPlaylistVideosLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val plInfo = PlaylistInfo.getInfo(plUrl)
                val items = plInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                ChannelCache.putPlaylistVideos(plUrl, items)
                withContext(Dispatchers.Main) {
                    playlistVideos = items
                    isPlaylistVideosLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isPlaylistVideosLoading = false
                }
            }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            if (activePlaylistUrl != null) {
                loadPlaylist(activePlaylistUrl!!, activePlaylistName ?: "", forceRefresh = true)
            } else {
                fetchChannelData(true)
            }
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            if (activePlaylistUrl != null || onBack != null) {
                TopAppBar(
                    title = {
                        if (activePlaylistName != null) {
                            Text(activePlaylistName!!)
                        }
                    },
                    navigationIcon = {
                        if (activePlaylistUrl != null) {
                            IconButton(onClick = { activePlaylistUrl = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Channel")
                            }
                        } else if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (activePlaylistUrl != null) {
                if (isPlaylistVideosLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val plUrls = remember(playlistVideos) { playlistVideos.map { it.url } }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlistVideos.size) { index ->
                            val item = playlistVideos[index]
                            YouTubeVideoCard(item = item, onClick = { onVideoSelected(item.url, plUrls, index) })
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Channel Info Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (channelAvatar != null) {
                            AsyncImage(
                                model = channelAvatar,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                        Column {
                            Text(channelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            val formattedSub = formatSubscriberCount(subscriberCount)
                            if (formattedSub.isNotBlank() && formattedSub != "-1") {
                                Text("$formattedSub subscribers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Inner Tabs: Videos & Playlists
                    TabRow(selectedTabIndex = selectedInnerTab) {
                        Tab(
                            selected = selectedInnerTab == 0,
                            onClick = { selectedInnerTab = 0 },
                            text = { Text("Videos") }
                        )
                        Tab(
                            selected = selectedInnerTab == 1,
                            onClick = { selectedInnerTab = 1 },
                            text = { Text("Playlists") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (selectedInnerTab == 0) {
                        val videoUrls = remember(videos) { videos.map { it.url } }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(videos.size) { index ->
                                val item = videos[index]
                                YouTubeVideoCard(item = item, onClick = { onVideoSelected(item.url, videoUrls, index) })
                            }
                        }
                    } else {
                        if (playlists.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No playlists found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 320.dp),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(playlists) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { loadPlaylist(item.url, item.name) },
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
                                                if (item.streamCount > 0) {
                                                    Surface(
                                                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                                        color = Color.Black.copy(alpha = 0.8f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "${item.streamCount} videos",
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
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

@Composable
fun YouTubeVideoCard(item: StreamInfoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
