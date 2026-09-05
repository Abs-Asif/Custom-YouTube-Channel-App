package durus.salafi.bangladesh.util

import org.schabi.newpipe.extractor.Image

fun getBestThumbnailUrl(thumbnails: List<Image>?): String? {
    if (thumbnails.isNullOrEmpty()) return null
    val best = thumbnails.maxByOrNull { img ->
        val area = if (img.width > 0 && img.height > 0) img.width * img.height else 0
        val level = img.estimatedResolutionLevel?.ordinal ?: 0
        area + level * 1000
    } ?: thumbnails.lastOrNull()
    return best?.url
}
