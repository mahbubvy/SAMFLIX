package com.potflix.data.repository

/** The season and episode numbers read from a video's file name; either may be unknown. */
data class EpisodeNumbers(val season: Int?, val episode: Int?)

private val SEASON_AND_EPISODE = Regex("""(?<![a-z])S(\d{1,2})[.\s_-]*E(\d{1,3})(?!\d)""", RegexOption.IGNORE_CASE)
private val SEASON_WORD_AND_EPISODE = Regex(
    """season[.\s_-]*(\d{1,2})[.\s_-]*(?:episode|ep|e)[.\s_-]*(\d{1,3})(?!\d)""",
    RegexOption.IGNORE_CASE
)
private val EPISODE_WORD = Regex("""(?<![a-z])(?:episode|ep|e)[.\s_-]*(\d{1,3})(?!\d)""", RegexOption.IGNORE_CASE)

// A number standing on its own, like the "05" in "Show - 05 [720p].mkv". Release tags in
// brackets are removed first, and "720p" style sizes don't count because a letter follows them.
private val TAGS = Regex("""\[[^\]]*]|\([^)]*\)|\{[^}]*}""")
private val BARE_NUMBER = Regex("""(?<![\da-z])(\d{1,3})(?![\da-z])""", RegexOption.IGNORE_CASE)

/**
 * Reads season and episode numbers from a file name such as "Show.S01E05.mkv",
 * "Show Season 1 Episode 5.mkv", "Show EP05.mkv" or "Show - 05 [1080p].mkv".
 */
fun parseEpisodeNumbers(fileName: String): EpisodeNumbers {
    val name = fileName.substringBeforeLast('.')
    (SEASON_AND_EPISODE.find(name) ?: SEASON_WORD_AND_EPISODE.find(name))?.let {
        return EpisodeNumbers(it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull())
    }
    EPISODE_WORD.find(name)?.let { return EpisodeNumbers(null, it.groupValues[1].toIntOrNull()) }
    val withoutTags = TAGS.replace(name, " ")
    val episode = BARE_NUMBER.findAll(withoutTags).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    return EpisodeNumbers(null, episode)
}
