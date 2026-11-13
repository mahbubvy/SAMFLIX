package com.potflix.data.local.entity

import androidx.annotation.Keep

@Keep
data class PreCrawledVideo(
    val name: String = "",
    val url: String = "",
    val quality: String = ""
)

/**
 * One catalogue row. The dhakaflix files store a single `url`, while other files
 * store a `videos` list, so both are optional and [resolvedVideos] normalises them.
 * Every field has a default so Gson uses the constructor and never leaves a
 * non-null Kotlin property holding null.
 */
@Keep
data class PreCrawledMovie(
    val title: String = "",
    val year: String? = null,
    val category: String = "",
    val posterUrl: String? = null,
    val tmdbId: Long? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val isTvSeries: Boolean = false,
    val videos: List<PreCrawledVideo> = emptyList(),
    val url: String? = null
) {
    fun resolvedVideos(): List<PreCrawledVideo> {
        val listed = videos.filter { it.url.isNotBlank() }
        if (listed.isNotEmpty()) return listed
        val single = url?.takeIf { it.isNotBlank() } ?: return emptyList()
        val fileName = single.trimEnd('/').substringAfterLast('/')
        val name = try {
            java.net.URLDecoder.decode(fileName.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            fileName
        }
        return listOf(PreCrawledVideo(name = name, url = single, quality = qualityOf(single)))
    }

    private fun qualityOf(address: String): String {
        val isFolder = address.endsWith("/") ||
            VIDEO_EXTENSIONS.none { address.endsWith(it, ignoreCase = true) }
        if (isFolder && category.contains("Series", ignoreCase = true)) return "Tv & Web Series"
        val lower = address.lowercase()
        return when {
            "2160p" in lower || "4k" in lower -> "4K"
            "1080p" in lower -> "1080p"
            "720p" in lower -> "720p"
            "480p" in lower -> "480p"
            else -> "720p"
        }
    }

    private companion object {
        val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".webm", ".ts", ".m4v", ".mov", ".wmv", ".flv", ".vob", ".m2ts")
    }
}
