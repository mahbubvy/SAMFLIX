package com.potflix.presentation.downloads

import com.potflix.data.local.entity.LocalDownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGroupsTest {
    private var nextId = 1L
    private fun dl(
        title: String, url: String = "http://s/x/$title.mkv", time: Long = nextId, series: String? = null,
        poster: String = "frame", cover: String? = null
    ) = LocalDownloadEntity(nextId++, title, url, null, poster, 8, 100, timestamp = time, seriesTitle = series, seriesPoster = cover)

    @Test
    fun episodesGroupUnderTheirShowInOrder() {
        val groups = groupDownloads(
            listOf(
                dl("Dark - S1E17 - Late", time = 30),
                dl("Dark - S1E10 - Early", time = 10),
                dl("Dark - S2E1 - Next season", time = 20),
                dl("Dark - S1E2 - Second", time = 5)
            )
        )
        assertEquals(1, groups.size)
        assertTrue(groups[0].isSeries)
        assertEquals("Dark", groups[0].title)
        assertEquals(listOf(2, 10, 17, 1), groups[0].items.map { it.episode })
        assertEquals(listOf(1, 1, 1, 2), groups[0].items.map { it.season })
    }

    @Test
    fun moviesStandAloneAndNewestGroupComesFirst() {
        val groups = groupDownloads(
            listOf(dl("Old Movie (2001)", time = 1), dl("Show - S1E1 - Pilot", time = 50), dl("New Movie", time = 99))
        )
        assertEquals(listOf("New Movie", "Show", "Old Movie (2001)"), groups.map { it.title })
        assertFalse(groups[0].isSeries)
    }

    @Test
    fun storedSeriesNameWinsAndFileNameFillsMissingNumbers() {
        val groups = groupDownloads(listOf(dl("Odd title", url = "http://s/Show/Show.S03E07.1080p.mkv", series = "Show")))
        assertEquals("Show", groups[0].title)
        assertEquals(3, groups[0].items[0].season)
        assertEquals(7, groups[0].items[0].episode)
    }

    @Test
    fun theShowsCoverBeatsEpisodeFrames() {
        val groups = groupDownloads(
            listOf(
                dl("Dark - S1E1 - A", poster = "frame1"),
                dl("Dark - S1E2 - B", poster = "frame2", cover = "dark-poster"),
                dl("Movie", poster = "frame3", cover = "movie-poster")
            )
        )
        assertEquals("dark-poster", groups.first { it.isSeries }.poster)
        assertEquals("movie-poster", groups.first { !it.isSeries }.poster)
    }
}
