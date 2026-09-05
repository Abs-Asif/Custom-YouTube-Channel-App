package durus.salafi.bangladesh.util

import org.schabi.newpipe.extractor.Image

fun getBestThumbnailUrl(thumbnails: List<Image>?): String? {
    if (thumbnails.isNullOrEmpty()) return null
    val best = thumbnails.maxByOrNull { img ->
        val area = if (img.width > 0 && img.height > 0) img.width * img.height else 0
        val level = img.estimatedResolutionLevel?.ordinal ?: 0
        area + level * 1000
    } ?: thumbnails.lastOrNull()

    val url = best?.url ?: return null
    return upgradeThumbnailResolution(url)
}

fun upgradeThumbnailResolution(url: String?): String? {
    if (url.isNullOrBlank()) return null
    var bestUrl = url
    if (bestUrl.contains("ytimg.com") || bestUrl.contains("ggpht.com")) {
        if (bestUrl.contains("default.jpg") || bestUrl.contains("hqdefault.jpg") || bestUrl.contains("mqdefault.jpg") || bestUrl.contains("sddefault.jpg")) {
            bestUrl = bestUrl.replace(Regex("(default|hqdefault|mqdefault|sddefault)\\.jpg"), "maxresdefault.jpg")
        }
    }
    return bestUrl
}
