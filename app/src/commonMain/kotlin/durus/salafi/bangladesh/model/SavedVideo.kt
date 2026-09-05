package durus.salafi.bangladesh.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedVideo(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbUrl: String,
    val localUri: String? = null,
    val uploaderAvatarUrl: String? = null,
    val localThumbUri: String? = null,
    val localAvatarUri: String? = null
)

@Serializable
data class OfflinePreview(
    val title: String,
    val author: String,
    val thumbUrl: String,
    val maxResolution: String,
    val availableQualities: List<String>
)

@Serializable
data class PlaylistSource(
    val title: String? = null,
    val url: String
)

@Serializable
data class WatchRecord(
    val videoUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
