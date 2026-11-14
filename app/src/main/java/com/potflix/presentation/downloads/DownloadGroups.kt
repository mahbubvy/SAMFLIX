package com.potflix.presentation.downloads

import com.potflix.data.local.entity.LocalDownloadEntity
import com.potflix.data.repository.parseEpisodeNumbers

/** One downloaded title: a movie, or a series with its downloaded episodes. */
data class DownloadGroup(
    val key: String,
    val title: String,
    val poster: String?,
    val isSeries: Boolean,
    /** Episodes in watching order (S1E1 first); a movie has exactly one item. */
    val items: List<DownloadedItem>
) {
    val lastActivity: Long get() = items.maxOf { it.download.timestamp }
}

data class DownloadedItem(val download: LocalDownloadEntity, val season: Int?, val episode: Int?)

// Episode downloads are titled "Show - S1E5 - Episode name" by the detail screen.
private val EPISODE_TITLE = Regex("""^(.*?) - S(\d+)E(\d+)(?: - (.*))?$""")

/**
 * Groups downloads by show, so a series appears once with its episodes inside, and puts each
 * series' episodes in season and episode order. Groups with the latest download come first.
 */
fun groupDownloads(downloads: List<LocalDownloadEntity>): List<DownloadGroup> {
    val groups = LinkedHashMap<String, MutableList<DownloadedItem>>()
    val names = HashMap<String, String>()
    val posters = HashMap<String, String?>()
    val series = HashSet<String>()

    for (download in downloads) {
        val match = EPISODE_TITLE.find(download.title)
        val showName = download.seriesTitle?.takeIf { it.isNotBlank() } ?: match?.groupValues?.get(1)
        if (showName == null) {
            val key = "movie:${download.streamUrl}"
            groups[key] = mutableListOf(DownloadedItem(download, null, null))
            names[key] = download.title
            posters[key] = download.seriesPoster ?: download.poster
            continue
        }
        val fromFile = parseEpisodeNumbers(download.streamUrl.substringAfterLast('/'))
        val season = match?.groupValues?.get(2)?.toIntOrNull() ?: fromFile.season
        val episode = match?.groupValues?.get(3)?.toIntOrNull() ?: fromFile.episode
        val key = "series:${showName.trim().lowercase()}"
        groups.getOrPut(key) { mutableListOf() } += DownloadedItem(download, season, episode)
        names.putIfAbsent(key, showName.trim())
        // An episode's own picture is a frame from the video; prefer the show's poster.
        if (download.seriesPoster != null) posters[key] = download.seriesPoster
        else if (!posters.containsKey(key)) posters[key] = null
        series += key
    }

    return groups.map { (key, items) ->
        DownloadGroup(
            key = key,
            title = names.getValue(key),
            poster = posters[key] ?: items.firstNotNullOfOrNull { it.download.poster },
            isSeries = key in series,
            items = items.sortedWith(
                compareBy<DownloadedItem>({ it.season ?: Int.MAX_VALUE }, { it.episode ?: Int.MAX_VALUE }, { it.download.title })
            )
        )
    }.sortedByDescending { it.lastActivity }
}
