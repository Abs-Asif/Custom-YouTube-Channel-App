package islamic.video.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import islamic.video.app.ui.theme.AppTheme
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import islamic.video.app.model.SavedVideo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("fookus_prefs", Context.MODE_PRIVATE)

    // App Preferences
    val themeMode = mutableIntStateOf(0)
    val selectedTheme = mutableStateOf(AppTheme.Dynamic)
    
    val searchSource = mutableStateOf("YouTube")
    val activeFilter = mutableStateOf("Search")
    val history = mutableStateOf<List<SavedVideo>>(emptyList())
    val bookmarks = mutableStateOf<Map<String, List<SavedVideo>>>(mapOf("Watch Later" to emptyList()))
    val lastPlayedUrl = mutableStateOf<String?>(null)

    init {
        loadHistoryAndBookmarks()
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
