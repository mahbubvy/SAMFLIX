package com.potflix.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.potflix.data.local.entity.PreCrawledMovie
import com.potflix.data.remote.PotFlixScraper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CatalogueTest {
    private val folder = File("src/main/assets/database/dhakaflix")

    private fun load(file: File): List<PreCrawledMovie> {
        val type = object : TypeToken<List<PreCrawledMovie>>() {}.type
        return file.reader(Charsets.UTF_8).use { Gson().fromJson(it, type) }
    }

    @Test
    fun everyRowHasAnAddress() {
        val files = folder.listFiles { f -> f.extension == "json" }!!.toList()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val missing = load(file).filter { it.resolvedVideos().isEmpty() }
            assertEquals("${file.name} rows without an address", 0, missing.size)
        }
    }

    @Test
    fun tenLivesResolvesToItsFile() {
        val row = load(File(folder, "animation.json")).first { it.title == "10 Lives" }
        val video = row.resolvedVideos().single()
        assertTrue(video.url.endsWith(".mkv"))
        assertEquals("720p", video.quality)
    }

    @Test
    fun seriesFoldersAreMarkedAsSeries() {
        val row = load(File(folder, "tv_series.json")).first()
        assertEquals("Tv & Web Series", row.resolvedVideos().single().quality)
    }

    @Test
    fun scraperFindsVideoOnLiveServer() = runBlocking {
        val url = "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/%282024%29/10%20Lives%20%282024%29%20720p%20WEBRip/"
        assumeTrue("Server not reachable from here", reachable(url))
        val videos = PotFlixScraper.scrapeDirectory(url).filter { it.isVideo }
        assertEquals(1, videos.size)
        assertTrue(reachable(videos.single().url))
    }

    private fun reachable(url: String): Boolean = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 5000
        connection.responseCode in 200..299
    } catch (e: Exception) {
        false
    }
}
