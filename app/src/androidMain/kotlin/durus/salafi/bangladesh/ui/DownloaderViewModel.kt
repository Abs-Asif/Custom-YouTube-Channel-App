package durus.salafi.bangladesh.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import durus.salafi.bangladesh.ui.theme.AppTheme
import durus.salafi.bangladesh.model.SavedVideo
import durus.salafi.bangladesh.model.PlaylistSource
import durus.salafi.bangladesh.model.WatchRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class PlaylistData(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val videoCount: Int = 0,
    val videos: List<StreamInfoItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("durus_prefs", Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient()

    // App Preferences
    val themeMode = mutableIntStateOf(0)
    val selectedTheme = mutableStateOf(AppTheme.Dynamic)

    val searchSource = mutableStateOf("YouTube")
    val activeFilter = mutableStateOf("Home") // "Home", "History", "Bookmarks"
    val history = mutableStateOf<List<SavedVideo>>(emptyList())
    val bookmarks = mutableStateOf<Map<String, List<SavedVideo>>>(mapOf("Watch Later" to emptyList()))
    val lastPlayedUrl = mutableStateOf<String?>(null)

    // Playlists & Sources
    val playlistSources = mutableStateOf<List<PlaylistSource>>(emptyList())
    val loadedPlaylists = mutableStateOf<Map<String, PlaylistData>>(emptyMap())
    val isLoadingSources = mutableStateOf(false)
    val sourcesErrorMessage = mutableStateOf<String?>(null)

    // Local Search Query
    val localSearchQuery = mutableStateOf("")

    // Watch Records
    val watchRecords = mutableStateOf<Map<String, WatchRecord>>(emptyMap())

    init {
        loadHistoryAndBookmarks()
        loadWatchRecords()
        loadSourcesAndPlaylists()
    }

    private fun loadHistoryAndBookmarks() {
        try {
            val histStr = prefs.getString("history", "[]") ?: "[]"
            history.value = Json.decodeFromString(histStr)
            lastPlayedUrl.value = prefs.getString("last_played", null)
            val bmStr = prefs.getString("bookmarks_list", null)
            if (bmStr != null) {
                bookmarks.value = Json.decodeFromString(bmStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadWatchRecords() {
        try {
            val str = prefs.getString("watch_records", null)
            if (str != null) {
                watchRecords.value = Json.decodeFromString(str)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveWatchRecord(videoUrl: String, positionMs: Long, durationMs: Long) {
        if (videoUrl.isBlank()) return
        val current = watchRecords.value.toMutableMap()
        val record = WatchRecord(videoUrl, positionMs, durationMs, System.currentTimeMillis())
        current[videoUrl] = record
        watchRecords.value = current
        try {
            prefs.edit().putString("watch_records", Json.encodeToString(current)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getWatchRecord(videoUrl: String): WatchRecord? {
        return watchRecords.value[videoUrl]
    }

    fun loadSourcesAndPlaylists(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isLoadingSources.value = true
                sourcesErrorMessage.value = null
            }

            val sources = fetchSourcesJson()
            withContext(Dispatchers.Main) {
                playlistSources.value = sources
            }

            val playlistsMap = loadedPlaylists.value.toMutableMap()

            for (source in sources) {
                if (!forceRefresh && playlistsMap.containsKey(source.url) && playlistsMap[source.url]?.videos?.isNotEmpty() == true) {
                    continue
                }

                playlistsMap[source.url] = PlaylistData(
                    title = source.title ?: "Playlist",
                    url = source.url,
                    isLoading = true
                )
                withContext(Dispatchers.Main) {
                    loadedPlaylists.value = playlistsMap.toMap()
                }

                try {
                    val plInfo = PlaylistInfo.getInfo(source.url)
                    val items = plInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                    val plTitle = source.title?.takeIf { it.isNotBlank() } ?: plInfo.name ?: "Playlist"
                    val thumb = plInfo.thumbnails?.firstOrNull()?.url ?: items.firstOrNull()?.thumbnails?.firstOrNull()?.url

                    playlistsMap[source.url] = PlaylistData(
                        title = plTitle,
                        url = source.url,
                        thumbnailUrl = thumb,
                        videoCount = items.size,
                        videos = items,
                        isLoading = false
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    playlistsMap[source.url] = PlaylistData(
                        title = source.title ?: "Playlist",
                        url = source.url,
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load playlist"
                    )
                }

                withContext(Dispatchers.Main) {
                    loadedPlaylists.value = playlistsMap.toMap()
                }
            }

            withContext(Dispatchers.Main) {
                isLoadingSources.value = false
            }
        }
    }

    private fun fetchSourcesJson(): List<PlaylistSource> {
        val urls = listOf(
            "https://raw.githubusercontent.com/Abs-Asif/Custom-YouTube-Channel-App/main/sources.json",
            "https://github.com/Abs-Asif/Custom-YouTube-Channel-App/raw/main/sources.json",
            "https://raw.githubusercontent.com/Abs-Asif/Custom-YouTube-Channel-App/master/sources.json"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        return Json.decodeFromString<List<PlaylistSource>>(bodyStr)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Local fallback
        return listOf(
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3aEmBOBbT9AffJPynBzTfea"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3b1HjwpMVZZrWsUlCLdPQPm"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3Yqn1lhxbOcB2J66Cr18cTt"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3Zhj3g9bUv31OlDl8ESMJAX"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3bD9Ktg_O8Xp0iwpqf-mx_F"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3YndSRhUBEXBW04sHklIlLG"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3ZYgsA7VW0QvqC1xJGVhcja"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3YWzXpxLv_GM9horsXCSN_n"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3aQm2gWxdQyiXwmPoV8Sq5c"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3agULpPzLyj-c42yX_L1vTR"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3aJkasqXtFChUDtSL38pBL8"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLBSd9V2aTA3bNqN6BEEeaEYdZRDjrB_2N"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvfcpZhOUZHfVc14_rr_9Lql"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvdLzcviRVyQo22diWQIt1j0"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvd9cfaRyhwUA3bTVbpL-hzE"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvfbPHDZLEgU_kMFInpyoXp-"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvdW-QI4ZjvLsibUWFx_cpIl"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvf5wO-fjVylERjNrfolq8eK"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvcSQBjIhNjwirBi_rALxCeL"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvdYzCDoXvsK5HgTRhz2E-xL"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpvfW7dq0oKV9MtD8_DPAronk"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpve4OQrBTE-XV4Q91gtLPzR3"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL31nnk3uDpveU4Ckmq2dlcllWG9NDVsEP"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLYA6TONnYbVw"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLPoz5o7cI0DI"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLg_yXd5EU9leStTEKgZ9YC8iL1VhGgOjV"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLg_yXd5EU9lecNFMzqqaKC_MT_NwKcjm_"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLg_yXd5EU9ldKW9Ai8ZPZJmsJSCugLFkj"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLh00bXGWSenkeqngQHzLS2A4Dj9LaBC7p"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLh00bXGWSennpmXyZwZdSlY4xojDeHFoq")
        )
    }

    fun addBookmarkPlaylist(name: String) {
        if (!bookmarks.value.containsKey(name)) {
            val newMap = bookmarks.value.toMutableMap()
            newMap[name] = emptyList()
            bookmarks.value = newMap
            saveBookmarks()
        }
    }

    fun addVideoToBookmark(playlist: String, video: SavedVideo) {
        val newMap = bookmarks.value.toMutableMap()
        val currentList = newMap[playlist]?.toMutableList() ?: mutableListOf()
        if (currentList.none { it.url == video.url }) {
            currentList.add(0, video)
            newMap[playlist] = currentList
            bookmarks.value = newMap
            saveBookmarks()
        }
    }

    fun removeVideoFromBookmark(playlist: String, url: String) {
        val newMap = bookmarks.value.toMutableMap()
        val currentList = newMap[playlist]?.toMutableList() ?: return
        if (currentList.removeAll { it.url == url }) {
            newMap[playlist] = currentList
            bookmarks.value = newMap
            saveBookmarks()
        }
    }

    private fun saveBookmarks() {
        prefs.edit().putString("bookmarks_list", Json.encodeToString(bookmarks.value)).apply()
    }

    fun addToHistory(video: SavedVideo) {
        val current = history.value.toMutableList()
        current.removeAll { it.url == video.url }
        current.add(0, video)
        if (current.size > 50) current.removeLast()
        history.value = current
        lastPlayedUrl.value = video.url
        prefs.edit()
            .putString("history", Json.encodeToString(current))
            .putString("last_played", video.url)
            .apply()
    }

    fun removeFromHistory(video: SavedVideo) {
        val current = history.value.toMutableList()
        if (current.removeAll { it.url == video.url }) {
            history.value = current
            prefs.edit().putString("history", Json.encodeToString(current)).apply()
        }
    }

    fun setThemeMode(mode: Int) {
        themeMode.intValue = mode
    }

    fun setAppTheme(theme: AppTheme) {
        selectedTheme.value = theme
    }

    private val _externalUrl = MutableStateFlow<String?>(null)
    val externalUrl: StateFlow<String?> = _externalUrl

    fun handleSharedUrl(url: String) {
        _externalUrl.value = url
    }

    fun consumeSharedUrl() {
        _externalUrl.value = null
    }

    val newPipeQuery = mutableStateOf("")
    val newPipeResults = mutableStateOf<List<StreamInfoItem>>(emptyList())
}
