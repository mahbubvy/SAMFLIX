package com.potflix.presentation.browse

import com.potflix.data.remote.ScrapedEntry

data class BrowseSection(
    val group: String,
    val name: String,
    val url: String,
    /** Titles in this section open as episode lists rather than single movies. */
    val isSeries: Boolean = false
)

enum class BrowseKind { YEAR, FOLDER, TITLE, VIDEO }

data class BrowseItem(
    val name: String,
    val url: String,
    val kind: BrowseKind,
    val title: String,
    val year: Int? = null,
    val quality: String? = null
)

object BrowseRules {
    private const val FLIX_7 = "http://172.16.50.7/DHAKA-FLIX-7/"
    private const val FLIX_10 = "http://172.16.50.10/DHAKA-FLIX-10/"
    private const val FLIX_12 = "http://172.16.50.12/DHAKA-FLIX-12/"
    private const val FLIX_14 = "http://172.16.50.14/DHAKA-FLIX-14/"

    /** Same sections, in the same groups, as the DhakaFlix home page. */
    val sections = listOf(
        BrowseSection("English", "English Movies", "${FLIX_7}English%20Movies/"),
        BrowseSection("English", "English Movies 1080p", "${FLIX_14}English%20Movies%20%281080p%29/"),
        BrowseSection("English", "IMDb Top 250 Movies", "${FLIX_14}IMDb%20Top-250%20Movies/"),
        BrowseSection("Hindi & South", "Hindi Movies", "${FLIX_14}Hindi%20Movies/"),
        BrowseSection("Hindi & South", "South Movies Hindi Dubbed", "${FLIX_14}SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/"),
        BrowseSection("Hindi & South", "South Indian Movies", "${FLIX_14}SOUTH%20INDIAN%20MOVIES/South%20Movies/"),
        BrowseSection("Foreign & Bangla", "Foreign Movies", "${FLIX_7}Foreign%20Language%20Movies/"),
        BrowseSection("Foreign & Bangla", "Kolkata Bangla Movies", "${FLIX_7}Kolkata%20Bangla%20Movies/"),
        BrowseSection("Foreign & Bangla", "Satyajit Ray Films", "${FLIX_7}Kolkata%20Bangla%20Movies/Satyajit%20Ray%20Films/"),
        BrowseSection("TV Series", "TV & Web Series", "${FLIX_12}TV-WEB-Series/", isSeries = true),
        BrowseSection("TV Series", "Korean TV & Web Series", "${FLIX_14}KOREAN%20TV%20%26%20WEB%20Series/", isSeries = true),
        BrowseSection("TV Series", "Cartoon TV Series", "${FLIX_10}Anime%20%26%20Cartoon%20TV%20Series/", isSeries = true),
        BrowseSection("Animation", "Animation Movies", "${FLIX_14}Animation%20Movies/"),
        BrowseSection("Animation", "Animation Movies 1080p", "${FLIX_14}Animation%20Movies%20%281080p%29/"),
        BrowseSection("Animation", "3D Movies", "${FLIX_7}3D%20Movies/"),
        BrowseSection("More", "Documentary", "${FLIX_10}Documentary/", isSeries = true),
        BrowseSection("More", "WWE & AEW Wrestling", "${FLIX_10}WWE%20%26%20AEW%20Wrestling/", isSeries = true),
        BrowseSection("More", "Awards & TV Shows", "${FLIX_10}Awards%20%26%20TV%20Shows/", isSeries = true)
    )

    // "(2024)", "2001", "(1960-1994)", "(2009) & Before", "(1996) 1080p", "(2015 & Before) PPV"
    private val YEAR_FOLDER = Regex(
        """^\(?\d{4}(\s*-\s*\d{4})?(\s*&\s*before)?\)?(\s*(1080p|ppv))?(\s*&\s*before)?(\s*(1080p|ppv))?$""",
        RegexOption.IGNORE_CASE
    )
    // "007 James Bond Films Collection (1962-2015)" holds many films, so it opens as a folder.
    private val COLLECTION = Regex("""\b(collection|filmography)\b""", RegexOption.IGNORE_CASE)
    private val YEAR_IN_BRACKETS =Regex("""\(.*?((?:19|20)\d{2}).*?\)""")
    private val FOUR_DIGITS = Regex("""\d{4}""")
    private val LIST_NUMBER = Regex("""^\d{1,4}\.\s+""")
    private val QUALITY = Regex("""2160p|4k|1080p|720p|480p""", RegexOption.IGNORE_CASE)

    /** Sorts a folder listing: newest year first, then folders, titles and loose videos. */
    fun toItems(entries: List<ScrapedEntry>): List<BrowseItem> {
        val items = entries.mapNotNull(::toItem)
        val years = items.filter { it.kind == BrowseKind.YEAR }
            .sortedByDescending { sortYear(it.name) }
        return years + items.filter { it.kind == BrowseKind.FOLDER } +
            items.filter { it.kind == BrowseKind.TITLE } +
            items.filter { it.kind == BrowseKind.VIDEO }
    }

    private fun toItem(entry: ScrapedEntry): BrowseItem? {
        val name = entry.name.trim()
        val kind = when {
            entry.isVideo -> BrowseKind.VIDEO
            !entry.isDirectory -> return null
            YEAR_FOLDER.matches(name) -> BrowseKind.YEAR
            COLLECTION.containsMatchIn(name) -> BrowseKind.FOLDER
            YEAR_IN_BRACKETS.containsMatchIn(name) -> BrowseKind.TITLE
            else -> BrowseKind.FOLDER
        }
        return BrowseItem(
            name = name,
            url = entry.url,
            kind = kind,
            title = if (kind == BrowseKind.TITLE || kind == BrowseKind.VIDEO) cleanTitle(name) else name,
            year = YEAR_IN_BRACKETS.find(name)?.groupValues?.get(1)?.toIntOrNull(),
            quality = QUALITY.find(name)?.value
        )
    }

    /** "001. The Godfather (1972) 1080p" and "(1958) Jalsaghar" both give the bare title. */
    fun cleanTitle(name: String): String {
        val unnumbered = name.replace(LIST_NUMBER, "")
        val beforeBracket = unnumbered.substringBefore("(").trim()
        return beforeBracket.ifEmpty {
            unnumbered.substringAfter(")").substringBefore("(").trim().ifEmpty { unnumbered }
        }
    }

    private fun sortYear(name: String): Int =
        FOUR_DIGITS.findAll(name).mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: 0
}
