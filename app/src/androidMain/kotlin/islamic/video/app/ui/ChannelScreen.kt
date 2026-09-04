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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    url: String,
    onBack: (() -> Unit)? = null,
    onVideoSelected: (String) -> Unit
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

    LaunchedEffect(url) {
        scope.launch(Dispatchers.IO) {
            try {
                val channelInfo = ChannelInfo.getInfo(url)
                val tabs = channelInfo.tabs

                // Fetch videos tab
                val videoTabLink = tabs.firstOrNull { it.id.lowercase().contains("video") } ?: tabs.firstOrNull()
                if (videoTabLink != null) {
                    val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, videoTabLink)
                    videos = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                }

                // Fetch playlists tab if available
                val playlistTabLink = tabs.firstOrNull { it.id.lowercase().contains("playlist") }
                if (playlistTabLink != null) {
                    try {
                        val plTabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, playlistTabLink)
                        playlists = plTabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    channelName = channelInfo.name
                    channelAvatar = channelInfo.avatars?.firstOrNull()?.url
                    subscriberCount = channelInfo.subscriberCount.toString()
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

    fun loadPlaylist(plUrl: String, plName: String) {
        activePlaylistUrl = plUrl
        activePlaylistName = plName
        isPlaylistVideosLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val plInfo = PlaylistInfo.getInfo(plUrl)
                val items = plInfo.relatedItems.filterIsInstance<StreamInfoItem>()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activePlaylistName ?: channelName.ifEmpty { "Channel" }) },
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
    ) { padding ->
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(playlistVideos) { item ->
                        YouTubeVideoCard(item = item, onClick = { onVideoSelected(item.url) })
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Channel Info Header (No banner image)
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
                        if (subscriberCount.isNotBlank() && subscriberCount != "-1") {
                            Text("$subscriberCount subscribers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(videos) { item ->
                            YouTubeVideoCard(item = item, onClick = { onVideoSelected(item.url) })
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
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                            AsyncImage(
                                                model = item.thumbnails?.firstOrNull()?.url ?: "",
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
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
                                        Spacer(Modifier.height(8.dp))
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
}

@Composable
fun YouTubeVideoCard(item: StreamInfoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = item.thumbnails?.firstOrNull()?.url ?: "",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
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
            Spacer(Modifier.height(8.dp))
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
