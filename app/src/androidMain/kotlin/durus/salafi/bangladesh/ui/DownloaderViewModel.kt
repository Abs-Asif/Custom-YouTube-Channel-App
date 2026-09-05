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
import durus.salafi.bangladesh.util.getBestThumbnailUrl
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@kotlinx.serialization.Serializable
data class SimpleVideoItem(
    val url: String,
    val name: String,
    val uploaderName: String? = null,
    val duration: Long = 0L,
    val thumbnailUrl: String? = null
)

@kotlinx.serialization.Serializable
data class CachedPlaylist(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val videoCount: Int = 0,
    val videos: List<SimpleVideoItem> = emptyList()
)

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

    // App Preferences - Default and permanent settings: Dark (2) and Rose (AppTheme.Rose)
    val themeMode = mutableIntStateOf(2)
    val selectedTheme = mutableStateOf(AppTheme.Rose)

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

    private fun loadCachedPlaylists(): Map<String, PlaylistData>? {
        return try {
            val cachedStr = prefs.getString("cached_playlists_data", null) ?: return null
            val cachedList = Json.decodeFromString<List<CachedPlaylist>>(cachedStr)
            val resultMap = mutableMapOf<String, PlaylistData>()
            for (cp in cachedList) {
                val streamItems = cp.videos.map { sv ->
                    val item = StreamInfoItem(0, sv.url, sv.name, org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM)
                    item.uploaderName = sv.uploaderName
                    item.duration = sv.duration
                    if (!sv.thumbnailUrl.isNullOrBlank()) {
                        item.thumbnails = listOf(org.schabi.newpipe.extractor.Image(sv.thumbnailUrl, 0, 0, org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN))
                    }
                    item
                }
                resultMap[cp.url] = PlaylistData(
                    title = cp.title,
                    url = cp.url,
                    thumbnailUrl = cp.thumbnailUrl,
                    videoCount = cp.videoCount,
                    videos = streamItems,
                    isLoading = false
                )
            }
            if (resultMap.isNotEmpty()) resultMap else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun savePlaylistsToCache(playlistsMap: Map<String, PlaylistData>) {
        try {
            val cachedList = playlistsMap.values.filter { !it.isLoading && it.videos.isNotEmpty() }.map { pl ->
                CachedPlaylist(
                    title = pl.title,
                    url = pl.url,
                    thumbnailUrl = pl.thumbnailUrl,
                    videoCount = pl.videoCount,
                    videos = pl.videos.map { v ->
                        SimpleVideoItem(
                            url = v.url,
                            name = v.name,
                            uploaderName = v.uploaderName,
                            duration = v.duration,
                            thumbnailUrl = getBestThumbnailUrl(v.thumbnails)
                        )
                    }
                )
            }
            prefs.edit().putString("cached_playlists_data", Json.encodeToString(cachedList)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isOnline(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return false
    }

    fun loadSourcesAndPlaylists(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val isDeviceOnline = isOnline()
            val cached = loadCachedPlaylists()

            if (!isDeviceOnline) {
                withContext(Dispatchers.Main) {
                    if (cached != null) {
                        loadedPlaylists.value = cached
                    }
                    isLoadingSources.value = false
                    sourcesErrorMessage.value = "You are offline. Please check your internet connection."
                }
                return@launch
            }

            val lastFetchTime = prefs.getLong("last_sources_fetch_time", 0L)
            val currentTime = System.currentTimeMillis()
            val ONE_HOUR_MS = 60 * 60 * 1000L

            if (!forceRefresh && (currentTime - lastFetchTime) < ONE_HOUR_MS) {
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        loadedPlaylists.value = cached
                        isLoadingSources.value = false
                    }
                    return@launch
                }
            }

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
                    val thumb = getBestThumbnailUrl(plInfo.thumbnails) ?: items.firstOrNull()?.let { getBestThumbnailUrl(it.thumbnails) }

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
                    val msg = if (!isOnline() || e is java.net.UnknownHostException || e is java.io.IOException) {
                        "You are offline. Please check your internet connection."
                    } else {
                        e.message ?: "Failed to load playlist"
                    }
                    playlistsMap[source.url] = PlaylistData(
                        title = source.title ?: "Playlist",
                        url = source.url,
                        isLoading = false,
                        errorMessage = msg
                    )
                }

                withContext(Dispatchers.Main) {
                    loadedPlaylists.value = playlistsMap.toMap()
                }
            }

            savePlaylistsToCache(playlistsMap)
            prefs.edit().putLong("last_sources_fetch_time", System.currentTimeMillis()).apply()

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
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLh00bXGWSennpmXyZwZdSlY4xojDeHFoq"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_BhWi7_kP22ThV9WSdVREGs"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_AAdaeywPcoNLjWRgDQz4Q-"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_CpJk2GeVCyNTjgQZ6esMHS"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_CRydcXxSmpIfRhlP46a61O"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_Acjcps6cjXqqcyD_Md_oP2"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_CDbkm437H3xFMnTGVD0U-y"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_A-LDu9urAWxZ4sIt8IkbnF"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_AXTqlFFLSU0f1AAsD5DwEy"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_CMd_ebSlzAb9gEjAE712Rq"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_C9QXMB-qDdzpYXbWaG1F-a"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIjnWXUr1P_CfwrTtU-T84dvgwoGAAQNS"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLPk9HADQN3tROP7RRM6rc_JvjYKKyS9kI"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLPk9HADQN3tQry7jzcf3lXTsohp1-ysil"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF8gHt7rKelVdeiuOu78-2p7"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF96W5XVpDPq0i3HhAJtSSTH"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF_dk_BJRTk-207DgRtRsSCa"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF_tDfi4Rn1cHBCEJflcNxJQ"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF-KXXuduO83G-gPscTEkVdt"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF8QlN0k1rmc05BtIvuxvbO2"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF9gxRDM24pM71W_HhvamQRI"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyKlImD5_nF_yTXJhXQWXKGM-m5k-NZuK"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLC8i7juNesTVL6S5eXrYEMCMgpa9x8VTh"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLC8i7juNesTVUq3Oqn6Kr0JcqKxMMbmDk"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLC8i7juNesTV1N6DwrE5k3ks1ibhx2lLi"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLC8i7juNesTXvKFNfIlbjFn-8KTYQuToX"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1cMzqW6osE_JBH0Kyw2nTl"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0C4LwZT3lMIfdLdBTTzdDX"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2lorNxXdjI-w5FUXiDFsAY"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf33dAOuxFgp1dGdIt1WOQDC"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1tQhTc9V9G9orih7TpT2EF"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1L9Zuui0L08EAjrMY_o5se"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2w--v8QfEw3IfcIwfnjUVI"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0-m4uqNc6nHs6RuLEY2Wrb"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf30kHfuWkwZUUDRAb08jPOj"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0noklSV-XrOnenhWGBJ2Yq"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2BaB7A0xo1k4ib5p_QjvHA"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2vsG6PIXH7j2trTLk4VNh9"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3ZPUOV06XZ_9VuGJLoR5BE"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf28T-FMJhMs-gnQPaaW5qLY"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2KMzPKAjPtU2zppRYjFQHa"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0elGaK42N-Sa6bzsXu4SOq"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3seF94QES81WRk3atjQ_LC"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0aYYcnaufK4SzwJXmrqrsY"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0mxvJW0OH11m3rqCKVQw-S"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1MLiLjUlbDSb9s5EpHbqZR"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf13TAqL0M26_nU_2cyG32yi"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf31xmuhjTvtBr4HTv03o45V"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0O2qeSU7FDpJOWUWIybAxo"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2WaOW9uXqgin91TpnYd3vR"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0YtXOmBPD92Q5bgJGDPSzE"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3B-M6iVPdeJQGkyXHFnCrq"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf13ZSTk0wN2CDF85h555ljx"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1teqJBsicaUywf-QA3-U6H"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0Xaj6TrzR7gZ7VBApUy42D"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2skbunYKfTUBqKPgbtWOZN"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3gITXvncTgYUAtiq8_bbnO"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3uKynQd7v_x2YjZ33zJBPQ"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf0wNpLfZpVCjz-xgJpUQqXA"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf04o8hVJrs30mito0-svCMv"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1QQAKGhQd4fdd3_ENo0JYi"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf06NIWccPHg6RqUHR7ia5oD"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf19s5zigTa4KfoNkyzLlYxa"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf2B6wo2h3-zcPHaxpDWSMbu"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf223Tg-hBjZ9g4Es4dshcbm"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3_sN3dC1UV-Xz7LwXOBP5F"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1QBC-1phliSYyjvzvNXIXw"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf3fhc-pSkrhzI_N0_ASl1u0"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLA0cV1UDgWf1EGgBVJpiGZh-esP6LSHQb"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLLiucWRP0jd4"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PL4t9900UvRaI6IgG0VbQIRyCa0pAPeTjg"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyqCR31AOBozELxwc3Gl7XVXYVY-D4H9R"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyqCR31AOBowJjUTIGmSsRYV7Ac2e3ZkK"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLyqCR31AOBoyGOTtNXsjAK8-ctOLtn8ax"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLMD1byl0STao"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAOASFrpGWIUfHtmaOQI_ncW"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMppCcfsknimWW49Lw5Gkd_"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMhY-LkBdsYmOh_4wy3f1Sg"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAPtcuxlBoldK4eTC7Y5cWVu"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMI0p4pClKTKXrEKYguveAE"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMy8HtGnr0k8U9HZ164GdhR"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAPYmBX8EQnCZa4JR99KMM-T"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMHANPJLXHeTYGUuKu20kIQ"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMeytyPm_ppG9NDVEm-eyCW"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAOcPV4vHksuIfx1jIf3n7k_"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeANSDAruCdzaj2J14dWkGx6D"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMFAtkyvCJ8QVchMBwDJNQH"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAPmTFVbq6VZf9iXI4PmRUG-"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeANFXb7A0SsUdY1TX4FgA2Wx"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAODpGRSP6IM-dPPy2pRIGlM"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAOx-87T0GJrUVNm90R2tL5g"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMSnmhRt9byfbOE4FlzSUsX"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMnDrpua0MPC5SSEOhoIck1"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAM7WmfrlKx4uMeME9zJsfFk"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAM9fc7mQLLjhbg5iykJOLbU"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAMwmMrN3ZdO7TWxlCaDI77O"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAOqBBaz2KjckY7mqUsUxYTQ"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeAPXWAurHFdt05-kO59Y-gej"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLtKJcguEdeANqBa0QWv2Lfon_SNo2olTD"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLXg0IFX7hI4MQYxf3906I2mZt7jrCaFfS"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLXg0IFX7hI4P7sFnqcLEoGPWdIXK3wbyN"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIlYR86sCMmPnad0bQiyAPEdb99_EFpUI"),
            PlaylistSource(url = "https://m.youtube.com/playlist?list=PLIlYR86sCMmNmZ3qK7RX6i6YClQLey7Hh")
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
