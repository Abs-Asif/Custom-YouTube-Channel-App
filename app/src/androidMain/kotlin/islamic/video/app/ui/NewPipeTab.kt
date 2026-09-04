package islamic.video.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateDpAsState

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import islamic.video.app.model.SavedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NewPipeTab(
    viewModel: DownloaderViewModel,
    contentPadding: PaddingValues,
    onUrlSelected: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    var query by viewModel.newPipeQuery
    var results by viewModel.newPipeResults
    var isLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var currentExtractor by remember { mutableStateOf<org.schabi.newpipe.extractor.search.SearchExtractor?>(null) }
    var currentPage by remember { mutableStateOf<org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<org.schabi.newpipe.extractor.InfoItem>?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    val endOfListReached by remember {
        androidx.compose.runtime.derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= results.size - 2
        }
    }

    LaunchedEffect(endOfListReached) {
        if (endOfListReached && !isLoadingMore && !isLoading && currentPage?.hasNextPage() == true) {
            isLoadingMore = true
            withContext(Dispatchers.IO) {
                try {
                    val nextPage = currentExtractor!!.getPage(currentPage!!.nextPage)
                    val items = nextPage.items.filterIsInstance<StreamInfoItem>()
                    withContext(Dispatchers.Main) {
                        results = results + items
                        currentPage = nextPage
                    }
                } catch (e: Exception) { e.printStackTrace() }
                withContext(Dispatchers.Main) { isLoadingMore = false }
            }
        }
    }
    val selectedFilter = viewModel.activeFilter.value
    val setSelectedFilter = { filter: String -> viewModel.activeFilter.value = filter } // "Search", "History", "Bookmarks"
    val coroutineScope = rememberCoroutineScope()

    val history by viewModel.history
    var videoToDelete by remember { mutableStateOf<Pair<SavedVideo, String>?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val performSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (query.isNotBlank()) {
            setSelectedFilter("Search")
            isLoading = true
            searchJob?.cancel()
            searchJob = coroutineScope.launch(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val searchExtractor = service.getSearchExtractor(query)
                    searchExtractor.fetchPage()
                    val items = searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                    withContext(Dispatchers.Main) {
                        results = items
                        currentExtractor = searchExtractor
                        currentPage = searchExtractor.initialPage
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
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            if (query.isNotBlank()) {
                performSearch()
            }
            pullToRefreshState.endRefresh()
        }
    }

    androidx.compose.animation.AnimatedContent(targetState = selectedFilter, label = "filter_transition") { currentFilter ->
        if (currentFilter != "Search") {
            BackHandler {
                if (currentFilter.startsWith("Bookmark:")) {
                    setSelectedFilter("Bookmarks")
                } else {
                    setSelectedFilter("Search")
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentFilter) },
                        navigationIcon = {
                            IconButton(onClick = { setSelectedFilter("Search") }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (currentFilter == "Bookmarks") {
                        FloatingActionButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                        }
                    }
                },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            ) { innerPadding ->
                val listToShow = when {
                    currentFilter == "History" -> history
                    currentFilter.startsWith("Bookmark:") -> {
                        val playlistName = currentFilter.removePrefix("Bookmark: ")
                        viewModel.bookmarks.value[playlistName] ?: emptyList()
                    }
                    else -> emptyList()
                }
                Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp).fillMaxSize()) {

                    if (currentFilter == "Bookmarks") {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.bookmarks.value.keys.toList()) { playlistName ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { setSelectedFilter("Bookmark: $playlistName") },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(16.dp))
                                        Column {
                                            Text(playlistName, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            Text("${viewModel.bookmarks.value[playlistName]?.size ?: 0} videos", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (listToShow.isEmpty() && currentFilter != "Bookmarks") {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text("No items found.")
                        }
                    } else if (currentFilter != "Bookmarks") {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listToShow, key = { it.url + currentFilter }) { item ->
                                val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart || it == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                                            videoToDelete = Pair(item, currentFilter)
                                            false
                                        } else false
                                    }
                                )

                                androidx.compose.material3.SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().combinedClickable(
                                            onClick = {
                                                viewModel.addToHistory(item)
                                                onUrlSelected(item.url)
                                            },
                                            onLongClick = {
                                                if (currentFilter == "History") viewModel.removeFromHistory(item)
                                                else videoToDelete = Pair(item, currentFilter)
                                            }
                                        ),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(100.dp, 60.dp)) {
                                                AsyncImage(
                                                    model = item.thumbUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                Spacer(Modifier.height(4.dp))
                                                Text(item.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            BackHandler(enabled = query.isNotEmpty() || results.isNotEmpty()) {
                searchJob?.cancel()
                isLoading = false
                query = ""
                results = emptyList()
                currentExtractor = null
                currentPage = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {

                val isListEmpty = results.isEmpty() && !isLoading
                Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = isListEmpty,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(100.dp))
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "MadrasaTube",
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "MadrasaTube",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(visible = results.isNotEmpty() || isLoading) {
                            IconButton(
                                onClick = {
                                    searchJob?.cancel()
                                    isLoading = false
                                    query = ""
                                    results = emptyList()
                                    currentExtractor = null
                                    currentPage = null
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search YouTube...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchJob?.cancel()
                                        isLoading = false
                                        query = ""
                                        results = emptyList()
                                        currentExtractor = null
                                        currentPage = null
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                } else {
                                    IconButton(onClick = { performSearch() }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.large
                        )

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }

                    AnimatedVisibility(visible = isListEmpty) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            @Composable
                            fun AnimatedFilterChip(label: String, onClick: () -> Unit) {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(targetValue = if (isPressed) 50.dp else 8.dp)

                                FilterChip(
                                    selected = false,
                                    onClick = onClick,
                                    label = { Text(label) },
                                    shape = RoundedCornerShape(cornerRadius),
                                    interactionSource = interactionSource
                                )
                            }
            
                            AnimatedFilterChip("History") { setSelectedFilter("History") }
                            AnimatedFilterChip("Bookmarks") { setSelectedFilter("Bookmarks") }
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        if (results.isNotEmpty()) {
                            LazyVerticalGrid(state = listState, columns = GridCells.Adaptive(minSize = 350.dp), modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(results) { item ->
                                    val savedItem = SavedVideo(item.url, item.name, item.uploaderName, item.thumbnails?.firstOrNull()?.url ?: "")
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.addToHistory(savedItem)
                                            onUrlSelected(item.url)
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(100.dp, 60.dp)) {
                                                AsyncImage(
                                                    model = savedItem.thumbUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(savedItem.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                Spacer(Modifier.height(4.dp))
                                                Text(savedItem.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + contentPadding.calculateBottomPadding()),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val context = LocalContext.current
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                    FloatingActionButton(
                        onClick = {
                            val clipboardText = clipboardManager.getText()?.text
                            if (clipboardText != null && (clipboardText.contains("youtube.com") || clipboardText.contains("youtu.be"))) {
                                onUrlSelected(clipboardText)
                            } else {
                                Toast.makeText(context, "Not a YouTube link! Please try with a valid link.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste YouTube Link")
                    }

                    val lastPlayed = viewModel.lastPlayedUrl.value
                    if (lastPlayed != null) {
                        val fabInteraction = remember { MutableInteractionSource() }
                        val fabPressed by fabInteraction.collectIsPressedAsState()
                        val fabHovered by fabInteraction.collectIsHoveredAsState()
                        val isFabActive = fabPressed || fabHovered
                        val animatedRadius by animateDpAsState(targetValue = if (isFabActive) 50.dp else 12.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))

                        ExtendedFloatingActionButton(
                            onClick = { onUrlSelected(lastPlayed) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Continue playing") },
                            text = { Text("Continue") },
                            shape = RoundedCornerShape(animatedRadius),
                            interactionSource = fabInteraction
                        )
                    }
                }

                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

            }
        }
    }

    if (videoToDelete != null) {
        val (video, filterContext) = videoToDelete!!
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text(if (filterContext == "History") "Clear History" else "Delete Bookmark") },
            text = { 
                Text(
                    if (filterContext == "History") "Are you sure you want to clear '${video.title}' from your watch history?"
                    else "Are you sure you want to remove '${video.title}'?"
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    if (filterContext == "History") viewModel.removeFromHistory(video)
                    else if (filterContext.startsWith("Bookmark:")) {
                        val playlistName = filterContext.removePrefix("Bookmark: ")
                        viewModel.removeVideoFromBookmark(playlistName, video.url)
                    }
                    videoToDelete = null
                }) {
                    Text(if (filterContext == "History") "Clear" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.addBookmarkPlaylist(newPlaylistName.trim())
                    }
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
