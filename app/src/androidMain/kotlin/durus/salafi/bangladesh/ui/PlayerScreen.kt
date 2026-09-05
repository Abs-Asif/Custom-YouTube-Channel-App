package durus.salafi.bangladesh.ui

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.net.Uri
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import durus.salafi.bangladesh.model.SavedVideo

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    url: String,
    playlistUrls: List<String> = emptyList(),
    initialIndex: Int = 0,
    viewModel: DownloaderViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember(initialIndex, playlistUrls) { mutableIntStateOf(initialIndex) }

    val currentVideoUrl = remember(url, playlistUrls, currentIndex) {
        if (playlistUrls.isNotEmpty() && currentIndex in playlistUrls.indices) {
            playlistUrls[currentIndex]
        } else {
            url
        }
    }

    var streamExtractor by remember { mutableStateOf<StreamExtractor?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showDownloadOptionSheet by remember { mutableStateOf(false) }

    val exoPlayer = remember(context) { durus.salafi.bangladesh.service.PlaybackService.getOrCreatePlayer(context) }

    DisposableEffect(context) {
        val intent = android.content.Intent(context, durus.salafi.bangladesh.service.PlaybackService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {}
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == androidx.media3.common.Player.STATE_ENDED) {
                    // Serial playback: auto advance if more videos in playlist
                    if (playlistUrls.isNotEmpty() && currentIndex + 1 < playlistUrls.size) {
                        currentIndex++
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Save watch record periodically during playback
    LaunchedEffect(currentVideoUrl, isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            if (totalDuration > 0) {
                viewModel.saveWatchRecord(currentVideoUrl, currentPosition, totalDuration)
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    BackHandler {
        onBack()
    }

    // Load video stream when currentVideoUrl changes unless already playing this stream
    LaunchedEffect(currentVideoUrl) {
        // Check if exoPlayer is already playing a stream or local file for this currentVideoUrl
        val currentTag = exoPlayer.currentMediaItem?.mediaId
        if (currentTag == currentVideoUrl && exoPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        withContext(Dispatchers.IO) {
            val localDownloadedFile = viewModel.getDownloadedFile(currentVideoUrl)
            if (localDownloadedFile != null) {
                withContext(Dispatchers.Main) {
                    val record = viewModel.downloadedVideos.value[currentVideoUrl]
                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                        .setMediaId(currentVideoUrl)
                        .setUri(Uri.fromFile(localDownloadedFile))
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(record?.title ?: "Downloaded Video")
                                .setArtworkUri(Uri.parse(record?.thumbnailUrl ?: ""))
                                .build()
                        )
                        .build()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()

                    val watchRecord = viewModel.getWatchRecord(currentVideoUrl)
                    if (watchRecord != null && watchRecord.positionMs > 0 && watchRecord.positionMs < watchRecord.durationMs - 5000) {
                        exoPlayer.seekTo(watchRecord.positionMs)
                    }

                    exoPlayer.playWhenReady = true
                    isLoading = false
                }
                return@withContext
            }

            try {
                if (!viewModel.isOnline()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "You are offline. Please check your internet connection."
                        isLoading = false
                    }
                    return@withContext
                }

                val service = NewPipe.getServiceByUrl(currentVideoUrl)
                val extractor = service.getStreamExtractor(currentVideoUrl)
                extractor.fetchPage()
                
                val bestVideo = extractor.videoStreams.maxByOrNull { it.resolution.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }
                
                withContext(Dispatchers.Main) {
                    streamExtractor = extractor
                    streamUrl = bestVideo?.content
                    if (streamUrl != null) {
                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                            .setMediaId(currentVideoUrl)
                            .setUri(Uri.parse(streamUrl))
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(extractor.name)
                                    .setArtist(extractor.uploaderName)
                                    .setArtworkUri(Uri.parse(extractor.thumbnails?.firstOrNull()?.url ?: ""))
                                    .build()
                            )
                            .build()
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()

                        // Resume watch record if available
                        val record = viewModel.getWatchRecord(currentVideoUrl)
                        if (record != null && record.positionMs > 0 && record.positionMs < record.durationMs - 5000) {
                            exoPlayer.seekTo(record.positionMs)
                        }

                        exoPlayer.playWhenReady = true
                    } else {
                        errorMessage = "No playable stream found"
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = if (!viewModel.isOnline() || e is java.net.UnknownHostException || e is java.io.IOException) {
                        "You are offline. Please check your internet connection."
                    } else {
                        e.message ?: "Failed to load video"
                    }
                    errorMessage = msg
                    isLoading = false
                }
            }
        }
    }

    val activity = context as? Activity
    DisposableEffect(currentVideoUrl) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (exoPlayer.duration > 0) {
                viewModel.saveWatchRecord(currentVideoUrl, exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    var isInPipMode by remember { mutableStateOf((context as? Activity)?.isInPictureInPictureMode == true) }

    DisposableEffect(context) {
        val compActivity = context as? androidx.activity.ComponentActivity
        val observer = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        compActivity?.addOnPictureInPictureModeChangedListener(observer)
        onDispose {
            compActivity?.removeOnPictureInPictureModeChangedListener(observer)
        }
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isPhysicalLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by remember { mutableStateOf(if (!isTablet) isPhysicalLandscape else false) }

    LaunchedEffect(isPhysicalLandscape) {
        if (!isTablet) {
            isFullscreen = isPhysicalLandscape
        }
    }

    val hideUi = isInPipMode || isFullscreen

    val view = LocalView.current
    val window = (context as? Activity)?.window
    LaunchedEffect(isFullscreen) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    var isMusicMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (!hideUi) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (playlistUrls.isNotEmpty()) "Playing (${currentIndex + 1}/${playlistUrls.size})" else "Playing",
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (!hideUi) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (playlistUrls.size > 1) {
                                IconButton(
                                    onClick = { if (currentIndex > 0) currentIndex-- },
                                    enabled = currentIndex > 0
                                ) {
                                    Icon(Icons.Default.SkipPrevious, "Previous Video")
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                IconButton(onClick = {
                                    (context as? Activity)?.enterPictureInPictureMode(
                                        android.app.PictureInPictureParams.Builder()
                                            .setAspectRatio(android.util.Rational(16, 9))
                                            .build()
                                    )
                                }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.PictureInPicture, "PIP", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }

                            IconButton(onClick = {
                                isMusicMode = !isMusicMode
                            }) {
                                Icon(
                                    if (isMusicMode) Icons.Default.Headset else Icons.Default.HeadsetOff, 
                                    "Music Mode", 
                                    tint = if (isMusicMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                                val isDownloaded = viewModel.isDownloaded(currentVideoUrl)
                                IconButton(onClick = {
                                    showDownloadOptionSheet = true
                                }) {
                                    Icon(
                                        imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                        contentDescription = "Download Options",
                                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                            if (playlistUrls.size > 1) {
                                IconButton(
                                    onClick = { if (currentIndex + 1 < playlistUrls.size) currentIndex++ },
                                    enabled = currentIndex + 1 < playlistUrls.size
                                ) {
                                    Icon(Icons.Default.SkipNext, "Next Video")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (errorMessage?.contains("bot", ignoreCase = true) == true || errorMessage?.contains("captcha", ignoreCase = true) == true || errorMessage?.contains("blocked", ignoreCase = true) == true) {
                                Text("YouTube is likely blocking your IP. A VPN can help bypass this restriction.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Button(onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("market://details?id=ch.protonvpn.android")
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        intent.data = Uri.parse("https://play.google.com/store/apps/details?id=ch.protonvpn.android")
                                        context.startActivity(intent)
                                    }
                                }) {
                                    Text("Get Proton VPN")
                                }
                            }
                        }
                    }
                } else {
                    if (!isMusicMode) {
                        val playerModifier = if (isFullscreen || isInPipMode) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        }

                        Surface(
                            modifier = playerModifier,
                            color = androidx.compose.ui.graphics.Color.Black,
                            shadowElevation = if (isFullscreen || isInPipMode) 0.dp else 12.dp
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val viewLayout = android.view.LayoutInflater.from(ctx).inflate(durus.salafi.bangladesh.R.layout.player_view_layout, null) as PlayerView
                                    viewLayout.apply {
                                        player = exoPlayer
                                        useController = !isInPipMode
                                        layoutParams = android.widget.FrameLayout.LayoutParams(
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        setFullscreenButtonClickListener { isFullScreenMode ->
                                            isFullscreen = isFullScreenMode
                                            val act = context as? Activity
                                            if (isFullScreenMode) {
                                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            } else {
                                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                        }

                                        val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                                val newFullscreen = !isFullscreen
                                                isFullscreen = newFullscreen
                                                val act = context as? Activity
                                                if (newFullscreen) {
                                                    act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                } else {
                                                    act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                                }
                                                return true
                                            }
                                        })
                                        setOnTouchListener { _, event ->
                                            gestureDetector.onTouchEvent(event)
                                            false
                                        }
                                    }
                                },
                                update = { viewLayout ->
                                    viewLayout.useController = !isInPipMode
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        val musicModifier = if (isFullscreen || isInPipMode) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        }

                        val thumbnailUrl = streamExtractor?.thumbnails?.firstOrNull()?.url
                            ?: viewModel.downloadedVideos.value[currentVideoUrl]?.thumbnailUrl

                        Surface(
                            modifier = musicModifier,
                            color = androidx.compose.ui.graphics.Color.Black,
                            shadowElevation = if (isFullscreen || isInPipMode) 0.dp else 12.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (!thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(32.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)
                                    ) {}
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .padding(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Headset,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        val posMinutes = (currentPosition / 1000) / 60
                                        val posSeconds = (currentPosition / 1000) % 60
                                        val durMinutes = (totalDuration / 1000) / 60
                                        val durSeconds = (totalDuration / 1000) % 60
                                        val timeText = "%d:%02d / %d:%02d".format(posMinutes, posSeconds, durMinutes, durSeconds)

                                        if (totalDuration > 0) {
                                            Slider(
                                                value = currentPosition.toFloat(),
                                                onValueChange = { newPos ->
                                                    exoPlayer.seekTo(newPos.toLong())
                                                },
                                                valueRange = 0f..totalDuration.toFloat(),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                timeText,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        FilledIconButton(
                                            onClick = {
                                                if (exoPlayer.isPlaying) {
                                                    exoPlayer.pause()
                                                } else {
                                                    exoPlayer.play()
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (isFullscreen && !isMusicMode) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(horizontal = 32.dp, vertical = 24.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val act = context as? Activity
                                        act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.FullscreenExit, "Exit Fullscreen", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }

                    if (!hideUi) {
                        val titleText = streamExtractor?.name
                            ?: viewModel.downloadedVideos.value[currentVideoUrl]?.title

                        if (titleText != null) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(titleText, style = MaterialTheme.typography.titleLarge)

                                if (playlistUrls.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Playlist Videos",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    // Find current loaded playlist data from viewModel
                                    val currentPlaylistData = viewModel.loadedPlaylists.value.values.firstOrNull { pl ->
                                        pl.videos.map { it.url } == playlistUrls
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(playlistUrls.size) { idx ->
                                            val itemUrl = playlistUrls[idx]
                                            val videoItem = currentPlaylistData?.videos?.getOrNull(idx)
                                            val isSelected = idx == currentIndex

                                            val itemTitle = videoItem?.name ?: if (isSelected && titleText != null) titleText else "Video ${idx + 1}"
                                            val thumbUrl = durus.salafi.bangladesh.util.getBestThumbnailUrl(videoItem?.thumbnails)
                                                ?: streamExtractor?.thumbnails?.firstOrNull()?.url.takeIf { isSelected }

                                            val durationText = if (videoItem != null && videoItem.duration > 0) {
                                                val mins = videoItem.duration / 60
                                                val secs = videoItem.duration % 60
                                                "%d:%02d".format(mins, secs)
                                            } else null

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { currentIndex = idx },
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(100.dp)
                                                            .aspectRatio(16f / 9f)
                                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    ) {
                                                        if (thumbUrl != null) {
                                                            AsyncImage(
                                                                model = thumbUrl,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                            )
                                                        }
                                                        if (durationText != null) {
                                                            Surface(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .padding(4.dp),
                                                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                                                            ) {
                                                                Text(
                                                                    text = durationText,
                                                                    color = androidx.compose.ui.graphics.Color.White,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = itemTitle,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                                            maxLines = 2,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (isSelected) {
                                                            Text(
                                                                text = "Now Playing",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
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
                }
            }
        }
    }

    if (showDownloadOptionSheet) {
        VideoOptionBottomSheet(
            target = VideoDownloadTarget(
                url = currentVideoUrl,
                title = streamExtractor?.name ?: "Video",
                thumbnailUrl = streamExtractor?.thumbnails?.firstOrNull()?.url
            ),
            viewModel = viewModel,
            onDismiss = { showDownloadOptionSheet = false }
        )
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("Save to playlist") },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(viewModel.bookmarks.value.keys.toList()) { playlist ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val isSavedInThisPlaylist = viewModel.bookmarks.value[playlist]?.any { it.url == currentVideoUrl } == true
                                    if (isSavedInThisPlaylist) {
                                        viewModel.removeVideoFromBookmark(playlist, currentVideoUrl)
                                    } else {
                                        val video = SavedVideo(
                                            url = currentVideoUrl,
                                            title = streamExtractor?.name ?: "",
                                            uploader = streamExtractor?.uploaderName ?: "",
                                            thumbUrl = streamExtractor?.thumbnails?.firstOrNull()?.url ?: ""
                                        )
                                        viewModel.addVideoToBookmark(playlist, video)
                                    }
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isSavedInThisPlaylist = viewModel.bookmarks.value[playlist]?.any { it.url == currentVideoUrl } == true
                                Icon(
                                    if (isSavedInThisPlaylist) Icons.Default.Star else Icons.Default.StarBorder, 
                                    contentDescription = null,
                                    tint = if (isSavedInThisPlaylist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(playlist)
                                if (isSavedInThisPlaylist) {
                                    Spacer(Modifier.weight(1f))
                                    Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("New playlist") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.addBookmarkPlaylist(newPlaylistName)
                                val video = SavedVideo(
                                    url = currentVideoUrl,
                                    title = streamExtractor?.name ?: "",
                                    uploader = streamExtractor?.uploaderName ?: "",
                                    thumbUrl = streamExtractor?.thumbnails?.firstOrNull()?.url ?: ""
                                )
                                viewModel.addVideoToBookmark(newPlaylistName, video)
                                newPlaylistName = ""
                                showBookmarkDialog = false
                            }
                        }) {
                            Icon(Icons.Default.Add, "Create")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
