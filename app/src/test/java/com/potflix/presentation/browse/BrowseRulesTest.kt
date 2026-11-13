package com.potflix.presentation.browse

import com.potflix.data.remote.PotFlixScraper
import com.potflix.data.remote.ScrapedEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class BrowseRulesTest {
    private fun folder(name: String) = ScrapedEntry(name = name, url = "http://x/$name/", isDirectory = true)

    private fun kindOf(name: String) = BrowseRules.toItems(listOf(folder(name))).single().kind

    @Test
    fun recognisesEveryYearFolderStyle() {
        listOf(
            "(2024)", "2001", "(1960-1994)", "(2009) & Before", "2000 & Before",
            "(1996) 1080p", "(1995) 1080p & Before", "(2015 & Before) PPV", "(2016) PPV"
        ).forEach { assertEquals(it, BrowseKind.YEAR, kindOf(it)) }
    }

    @Test
    fun recognisesTitlesAndIndexFolders() {
        listOf(
            "10 Lives (2024) 720p WEBRip",
            "A Certain Magical Index (TV Cartoon 2008–2019) 1080p [Dual Audio]",
            "100 Days My Prince (TV Series 2018) 720p",
            "(1958) Jalsaghar",
            "001. The Shawshank Redemption (1994) 1080p [Dual Audio]"
        ).forEach { assertEquals(it, BrowseKind.TITLE, kindOf(it)) }
        listOf(
            "TV Series ★  0  —  9", "AEW Wrestling", "# AWARDS", "Chinese Language",
            "007 James Bond Films Collection (1962-2015) 1080p", "Charlie Chaplin-Complete Filmography (1914-1967)"
        )
            .forEach { assertEquals(it, BrowseKind.FOLDER, kindOf(it)) }
    }

    @Test
    fun newestYearComesFirst() {
        val names = BrowseRules.toItems(listOf("(1960-1994)", "(2026)", "(2009) & Before").map(::folder))
            .map { it.name }
        assertEquals(listOf("(2026)", "(2009) & Before", "(1960-1994)"), names)
    }

    @Test
    fun cleansTitles() {
        assertEquals("The Godfather", BrowseRules.cleanTitle("002. The Godfather (1972) 1080p"))
        assertEquals("Jalsaghar", BrowseRules.cleanTitle("(1958) Jalsaghar"))
        assertEquals("2.5 Dimensional Seduction", BrowseRules.cleanTitle("2.5 Dimensional Seduction (TV Series 2024)"))
    }

    @Test
    fun everySectionOpensOnLiveServer() = runBlocking {
        assumeTrue("Servers not reachable from here", reachable(BrowseRules.sections.first().url))
        val empty = mutableListOf<String>()
        for (section in BrowseRules.sections) {
            val items = BrowseRules.toItems(PotFlixScraper.fetchDirectory(section.url))
            println("${section.name}: ${items.groupingBy { it.kind }.eachCount()}")
            if (items.isEmpty()) empty += section.name
        }
        assertTrue("Empty sections: $empty", empty.isEmpty())
    }

    @Test
    fun blankPageFolderListsItsTitlesAndFiles() = runBlocking {
        val section = BrowseRules.sections.first { it.name == "3D Movies" }
        assumeTrue("Servers not reachable from here", reachable(BrowseRules.sections.first().url))
        val titles = BrowseRules.toItems(PotFlixScraper.fetchDirectory(section.url))
            .filter { it.kind == BrowseKind.TITLE }
        assertTrue(titles.size > 50)
        val inside = PotFlixScraper.fetchDirectory(titles.first().url)
        assertTrue("No video inside ${titles.first().name}", inside.any { it.isVideo })
    }

    private fun reachable(url: String): Boolean = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "HEAD"
            connectTimeout = 5000
            responseCode in 200..299
        }
    } catch (e: Exception) {
        false
    }
}
