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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.rememberScrollState
import durus.salafi.bangladesh.util.AiSummaryUtils
import kotlinx.coroutines.launch
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import durus.salafi.bangladesh.model.AdItem
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
    var showPlaylistCompletionDialog by remember { mutableStateOf(false) }
    var completedPlaylistUrl by remember { mutableStateOf<String?>(null) }

    // Ad Popup state
    var activeAdForPopup by remember { mutableStateOf<AdItem?>(null) }

    // AI Summary state
    var showAiSummarySheet by remember { mutableStateOf(false) }
    var isAiSummaryLoading by remember { mutableStateOf(false) }
    var aiSummaryResult by remember { mutableStateOf<String?>(null) }
    var aiSummaryError by remember { mutableStateOf<String?>(null) }
    var summaryVideoUrl by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun triggerAiSummary() {
        if (summaryVideoUrl == currentVideoUrl && (aiSummaryResult != null || isAiSummaryLoading)) {
            return
        }
        summaryVideoUrl = currentVideoUrl
        isAiSummaryLoading = true
        aiSummaryResult = null
        aiSummaryError = null

        coroutineScope.launch {
            val subStream = AiSummaryUtils.getBengaliSubtitleStream(currentVideoUrl, streamExtractor)
            if (subStream == null) {
                isAiSummaryLoading = false
                aiSummaryError = "এই ভিডিওটির জন্য কোনো বাংলা ক্যাপশন পাওয়া যায়নি।"
                return@launch
            }

            val cleanedText = AiSummaryUtils.fetchAndCleanSubtitle(subStream)
            if (cleanedText.isBlank()) {
                isAiSummaryLoading = false
                aiSummaryError = "ভিডিও ক্যাপশন সংগ্রহ করা সম্ভব হয়নি বা এতে কোনো প্রয়োজনীয় লেখা পাওয়া যায়নি।"
                return@launch
            }

            val res = AiSummaryUtils.generateAiSummary(cleanedText)
            isAiSummaryLoading = false
            res.onSuccess { summary ->
                aiSummaryResult = summary
            }.onFailure { err ->
                aiSummaryError = err.message ?: "সারসংক্ষেপ তৈরি করতে সমস্যা হয়েছে।"
            }
        }
    }

    LaunchedEffect(showAiSummarySheet, currentVideoUrl) {
        if (showAiSummarySheet) {
            triggerAiSummary()
        }
    }

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
                    } else if (playlistUrls.isNotEmpty() && currentIndex + 1 >= playlistUrls.size) {
                        // Check if user finished the last video of a playlist
                        val matchedPl = viewModel.loadedPlaylists.value.values.firstOrNull { pl ->
                            pl.videos.any { it.url == currentVideoUrl }
                        }
                        if (matchedPl != null && !viewModel.isPlaylistInBottom(matchedPl.url)) {
                            completedPlaylistUrl = matchedPl.url
                            showPlaylistCompletionDialog = true
                        }
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

    val handleBack = {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    BackHandler {
        handleBack()
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
                    val rawMsg = e.message ?: ""
                    val isNetError = !viewModel.isOnline() ||
                        e is java.net.UnknownHostException ||
                        e is java.io.IOException ||
                        rawMsg.contains("googleapis", ignoreCase = true) ||
                        rawMsg.contains("youtube", ignoreCase = true) ||
                        rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                        rawMsg.contains("Failed to connect", ignoreCase = true)

                    errorMessage = if (isNetError) {
                        "You are offline. Please check your internet connection."
                    } else {
                        rawMsg.ifBlank { "Failed to load video" }
                    }
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
                        IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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

                                IconButton(onClick = {
                                    showAiSummarySheet = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Summary",
                                        tint = if (showAiSummarySheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
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
                        val downloadedMap by viewModel.downloadedVideos
                        val historyList by viewModel.history
                        val loadedMap by viewModel.loadedPlaylists

                        val resolvedTitle = remember(streamExtractor, currentVideoUrl, downloadedMap, historyList, loadedMap) {
                            streamExtractor?.name
                                ?: downloadedMap[currentVideoUrl]?.title
                                ?: historyList.firstOrNull { it.url == currentVideoUrl }?.title
                                ?: loadedMap.values.flatMap { it.videos }.firstOrNull { it.url == currentVideoUrl }?.name
                                ?: "Video"
                        }

                        val matchedPlaylist = remember(currentVideoUrl, loadedMap) {
                            loadedMap.values.firstOrNull { pl -> pl.videos.any { it.url == currentVideoUrl } }
                        }

                        val effectivePlaylistUrls = remember(playlistUrls, matchedPlaylist, currentVideoUrl) {
                            if (playlistUrls.size > 1) {
                                playlistUrls
                            } else if (matchedPlaylist != null) {
                                matchedPlaylist.videos.map { it.url }
                            } else {
                                playlistUrls
                            }
                        }

                        // Find connected ad for the playlist
                        val connectedAd = remember(matchedPlaylist, viewModel.adsList.value) {
                            val playlistUrl = matchedPlaylist?.url
                            if (playlistUrl != null) {
                                viewModel.adsList.value.firstOrNull { it.connectedPlaylistUrl == playlistUrl }
                            } else null
                        }

                        // Check watch records count for videos in this playlist
                        LaunchedEffect(currentVideoUrl, matchedPlaylist, connectedAd, viewModel.watchRecords.value) {
                            if (connectedAd != null && matchedPlaylist != null) {
                                val adKey = "${connectedAd.connectedPlaylistUrl}_${connectedAd.affiliateUrl}"
                                if (!viewModel.isAdPopupShown(adKey)) {
                                    val watchedCount = matchedPlaylist.videos.count { v ->
                                        val rec = viewModel.watchRecords.value[v.url]
                                        if (rec != null && rec.durationMs > 0) {
                                            (rec.positionMs.toFloat() / rec.durationMs.toFloat()) >= 0.97f
                                        } else false
                                    }
                                    if (watchedCount >= 2) {
                                        activeAdForPopup = connectedAd
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    resolvedTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (effectivePlaylistUrls.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Playlist Videos",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(effectivePlaylistUrls.size) { idx ->
                                            val itemUrl = effectivePlaylistUrls[idx]
                                            val isSelected = itemUrl == currentVideoUrl || (effectivePlaylistUrls.size == playlistUrls.size && idx == currentIndex)

                                            val itemVideo = matchedPlaylist?.videos?.firstOrNull { it.url == itemUrl }
                                                ?: loadedMap.values.flatMap { it.videos }.firstOrNull { it.url == itemUrl }
                                            val historyVideo = historyList.firstOrNull { it.url == itemUrl }
                                            val downloadedRecord = downloadedMap[itemUrl]

                                            val itemTitle = itemVideo?.name
                                                ?: historyVideo?.title
                                                ?: downloadedRecord?.title
                                                ?: if (isSelected) resolvedTitle else "Video ${idx + 1}"

                                            val thumbUrl = durus.salafi.bangladesh.util.getBestThumbnailUrl(itemVideo?.thumbnails)
                                                ?: historyVideo?.thumbUrl
                                                ?: downloadedRecord?.thumbnailUrl
                                                ?: streamExtractor?.thumbnails?.firstOrNull()?.url.takeIf { isSelected }

                                            val durationText = if (itemVideo != null && itemVideo.duration > 0) {
                                                val mins = itemVideo.duration / 60
                                                val secs = itemVideo.duration % 60
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

                            if (showAiSummarySheet) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.BottomCenter),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 16.dp,
                                    shadowElevation = 16.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "এআই সারসংক্ষেপ",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (aiSummaryResult != null) {
                                                    IconButton(onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                        val clip = ClipData.newPlainText("AI Summary", aiSummaryResult)
                                                        clipboard?.setPrimaryClip(clip)
                                                        Toast.makeText(context, "সারসংক্ষেপ কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                                    }) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                                    }
                                                }
                                                IconButton(onClick = { showAiSummarySheet = false }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                                }
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.TopStart
                                        ) {
                                            if (isAiSummaryLoading) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                    Spacer(Modifier.height(16.dp))
                                                    Text(
                                                        "বাংলা ক্যাপশন সংগ্রহ ও এআই সারসংক্ষেপ তৈরি হচ্ছে...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            } else if (aiSummaryError != null) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState()),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        aiSummaryError!!,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.error,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    Spacer(Modifier.height(16.dp))
                                                    Button(onClick = {
                                                        summaryVideoUrl = null
                                                        triggerAiSummary()
                                                    }) {
                                                        Text("পুনরায় চেষ্টা করুন")
                                                    }
                                                }
                                            } else if (aiSummaryResult != null) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    Text(
                                                        text = aiSummaryResult!!,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
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

    if (activeAdForPopup != null) {
        val ad = activeAdForPopup!!
        val adKey = "${ad.connectedPlaylistUrl}_${ad.affiliateUrl}"

        // Mark ad popup as shown so it appears only once automatically
        LaunchedEffect(adKey) {
            viewModel.markAdPopupShown(adKey)
        }

        AlertDialog(
            onDismissRequest = { activeAdForPopup = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            confirmButton = {},
            dismissButton = {},
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Top header: Ad headline on top (big) + 'X' close button on top right (fixed, non-scrolling)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = ad.headline,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { activeAdForPopup = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Internally scrollable content area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Ad image (un-cropped, actual size)
                        if (ad.imageUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ad.imageUrl,
                                    contentDescription = ad.headline,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Affiliate link button (Buy now / "বইটি কিনুন", highlighted)
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(ad.affiliateUrl))
                                try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "বইটি কিনুন",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Ad description
                        Text(
                            text = ad.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    }

    if (showPlaylistCompletionDialog && completedPlaylistUrl != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistCompletionDialog = false },
            title = { Text("প্লেলিস্ট সম্পূর্ণ হয়েছে", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("আপনি এই প্লেলিস্টের সব ভিডিও দেখা সম্পন্ন করেছেন। আপনি কি চান এই প্লেলিস্টটি নিচে পাঠিয়ে দেওয়া হোক?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        completedPlaylistUrl?.let { viewModel.sendPlaylistToBottom(it) }
                        showPlaylistCompletionDialog = false
                    }
                ) {
                    Text("হ্যাঁ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistCompletionDialog = false }) {
                    Text("না")
                }
            }
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
