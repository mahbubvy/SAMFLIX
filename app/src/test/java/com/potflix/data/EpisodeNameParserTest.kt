package com.potflix.data

import com.potflix.data.repository.EpisodeNumbers
import com.potflix.data.repository.parseEpisodeNumbers
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeNameParserTest {
    @Test
    fun readsSeasonAndEpisodeCodes() {
        assertEquals(EpisodeNumbers(1, 5), parseEpisodeNumbers("Magi.The.Labyrinth.of.Magic.S01E05.1080p.mkv"))
        assertEquals(EpisodeNumbers(2, 12), parseEpisodeNumbers("Show s02 e12 HEVC.mp4"))
        assertEquals(EpisodeNumbers(1, 3), parseEpisodeNumbers("Show Season 1 Episode 3.mkv"))
    }

    @Test
    fun readsEpisodeWords() {
        assertEquals(EpisodeNumbers(null, 7), parseEpisodeNumbers("Magi EP07 [720p].mkv"))
        assertEquals(EpisodeNumbers(null, 11), parseEpisodeNumbers("Show Episode 11.mkv"))
    }

    @Test
    fun readsBareNumbersAndIgnoresTags() {
        assertEquals(EpisodeNumbers(null, 5), parseEpisodeNumbers("Magi - The Labyrinth of Magic - 05 [720p].mkv"))
        assertEquals(EpisodeNumbers(null, 2), parseEpisodeNumbers("[HorribleSubs] Magi - 02 [1080p][ABCD1234].mkv"))
        assertEquals(EpisodeNumbers(null, 24), parseEpisodeNumbers("Magi (2012) 24.mp4"))
    }

    @Test
    fun wordsEndingInEDoNotCountAsEpisodes() {
        // The old parser read "Online 2" as episode 2 because "Online" ends in "e".
        assertEquals(EpisodeNumbers(null, 9), parseEpisodeNumbers("Sword Art Online 2 - 09.mkv"))
    }

    @Test
    fun noNumberMeansUnknown() {
        assertEquals(EpisodeNumbers(null, null), parseEpisodeNumbers("Special Feature [720p].mkv"))
    }
}
