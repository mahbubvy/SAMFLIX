package com.potflix.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.potflix.data.local.dao.MovieDao
import com.potflix.data.remote.PotFlixApi
import com.potflix.data.remote.PotFlixScraper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.room.withTransaction
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.potflix.data.local.entity.PreCrawledMovie


@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val movieDao: MovieDao,
    private val database: com.potflix.data.local.PotFlixDatabase,
    private val api: PotFlixApi,
    private val serverPreferences: com.potflix.data.local.preferences.ServerPreferences,
    private val aListScraper: com.potflix.data.remote.AListScraper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val folderUrl = inputData.getString("folderUrl")
            val categoryName = inputData.getString("categoryName")
            val isTvSeries = inputData.getBoolean("isTvSeries", false)
            val isFullSync = inputData.getBoolean("isFullSync", false)
            val isInitialLoad = inputData.getBoolean("isInitialLoad", true)

            if (folderUrl != null && categoryName != null) {
                setProgress(workDataOf("progress_msg" to "Starting sync for $categoryName...", "progress_pct" to 0f))
                Log.d("SyncWorker", "Starting sync for $categoryName at $folderUrl")

                if (isTvSeries) {
                    syncTvSeries(categoryName, folderUrl)
                } else {
                    val yearsToSync = listOf("2026", "2025", "2024", "2023", "2022")
                    syncMovies(categoryName, folderUrl, yearsToSync, 0f, 100f, isFullSync)
                }
            } else {
                // AUTOMATIC FETCHING
                setProgress(workDataOf("progress_msg" to "Discovering server content...", "progress_pct" to 0f))
                
                // 1. If Initial Load, Try to load all JSON files from assets/database/{serverFolder}
                if (isInitialLoad) {
                    try {
                        val server = serverPreferences.activeServer.value
                        val serverFolder = if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) "nagordola" else "dhakaflix"
                        val dbFiles = applicationContext.assets.list("database/$serverFolder") ?: emptyArray()
                        val jsonFiles = dbFiles.filter { it.endsWith(".json") }
                        
                        if (jsonFiles.isNotEmpty()) {
                            Log.d("SyncWorker", "Found ${jsonFiles.size} pre-crawled database files. Bypassing live discovery.")
                            val pctPerFile = 100f / jsonFiles.size
                            for ((i, file) in jsonFiles.withIndex()) {
                                val categoryName = file.removeSuffix(".json").replace("_", "-")
                                val isTv = file.startsWith("tvshows") || file.contains("anime") && file.contains("tv")
                                val startPct = i * pctPerFile
                                val endPct = (i + 1) * pctPerFile
                                
                                if (isTv) {
                                    syncTvSeries(categoryName, "")
                                } else {
                                    syncMovies(categoryName, "", emptyList(), startPct, endPct, isFullSync = true)
                                }
                            }
                            setProgress(workDataOf("progress_msg" to "Sync completed successfully!", "progress_pct" to 100f))
                            return@withContext Result.success()
                        }
                    } catch (e: Exception) {
                        Log.e("SyncWorker", "Error processing local database files", e)
                    }
                }

                // 2. Fallback to live server discovery
                val server = serverPreferences.activeServer.value
                if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) {
                    val bases = listOf(
                        "Movies" to "${server.baseUrl}movies",
                        "TV Series" to "${server.baseUrl}tv-series",
                        "Anime" to "${server.baseUrl}anime"
                    )
                    var currentPct = 10f
                    val pctPerBase = 90f / bases.size
                    for ((parentType, baseUrl) in bases) {
                        try {
                            setProgress(workDataOf("progress_msg" to "Scanning $parentType...", "progress_pct" to currentPct))
                            val entries = aListScraper.scrapeDirectory(baseUrl)
                            val folders = entries.filter { it.isDirectory }
                            
                            val pctPerFolder = pctPerBase / Math.max(1, folders.size)
                            for ((i, f) in folders.withIndex()) {
                                val folderPct = currentPct + (i * pctPerFolder)
                                setProgress(workDataOf("progress_msg" to "Fetching ${f.name}...", "progress_pct" to folderPct))
                                
                                val isTv = parentType == "TV Series" || f.name.contains("tvshows", ignoreCase = true)
                                if (isTv) {
                                    syncTvSeries(f.name, f.url)
                                } else {
                                    syncMovies(f.name, f.url, listOf("2026", "2025", "2024", "2023"), folderPct, folderPct + pctPerFolder, isFullSync)
                                }
                            }
                        } catch (e: Exception) {}
                        currentPct += pctPerBase
                    }
                } else if (server.type == com.potflix.data.local.preferences.ServerType.FTP) {
                    val recentYears = listOf("2026", "2025", "2024", "2023")
                    val pctPerBase = 70f / LIVE_MOVIE_SECTIONS.size
                    for ((index, section) in LIVE_MOVIE_SECTIONS.withIndex()) {
                        val startPct = index * pctPerBase
                        syncMovies(section.first, section.second, recentYears, startPct, startPct + pctPerBase, isFullSync)
                    }
                    for (section in LIVE_SERIES_SECTIONS) {
                        syncTvSeries(section.first, section.second)
                    }
                }
            }

            setProgress(workDataOf("progress_msg" to "Sync completed successfully!", "progress_pct" to 100f))
            Log.d("SyncWorker", "Background sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.failure()
        }
    }

    private fun getPreCrawledData(categoryName: String): List<PreCrawledMovie>? {
        val server = serverPreferences.activeServer.value
        val serverFolder = if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) "nagordola" else "dhakaflix"
        val fileName = "${categoryName.replace(" ", "_").replace("-", "_").lowercase()}.json"
        return try {
            val inputStream = applicationContext.assets.open("database/$serverFolder/$fileName")
            val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
            val type = object : TypeToken<List<PreCrawledMovie>>() {}.type
            val result: List<PreCrawledMovie> = Gson().fromJson(reader, type)
            reader.close()
            result
        } catch (e: Exception) {
            Log.e("SyncWorker", "Failed to load pre-crawled data for $fileName", e)
            null
        }
    }

    /** Adds a title, or attaches a new quality to it when the same title already exists. */
    private suspend fun importCatalogueRow(item: PreCrawledMovie) {
        val movieId = movieDao.getMovieIdByTitleAndYear(item.title, item.year ?: "")
            ?: movieDao.insertMovie(
                com.potflix.data.local.entity.MovieEntity(
                    title = item.title,
                    year = item.year ?: "",
                    posterUrl = item.posterUrl,
                    category = item.category,
                    region = null,
                    tmdbId = item.tmdbId,
                    overview = item.overview,
                    rating = item.rating?.takeIf { it > 0.0 },
                    isImdbTop250 = item.category == "IMDB Top 250"
                )
            )
        if (movieId <= 0) return
        for (video in item.resolvedVideos()) {
            if (movieDao.countVideosWithUrl(movieId, video.url) == 0) {
                movieDao.insertVideo(
                    com.potflix.data.local.entity.VideoEntity(
                        movieId = movieId,
                        quality = video.quality,
                        url = video.url,
                        fileName = video.name
                    )
                )
            }
        }
    }

    private suspend fun syncMovies(categoryName: String, baseUrl: String, yearsToSync: List<String>, basePct: Float = 0f, maxPct: Float = 100f, isFullSync: Boolean = false) {
        val preCrawled = getPreCrawledData(categoryName)
        if (preCrawled != null && preCrawled.isNotEmpty()) {
            Log.d("SyncWorker", "Using pre-crawled database for $categoryName")
            setProgress(workDataOf("progress_msg" to "Importing $categoryName...", "progress_pct" to basePct))
            
            val toProcess = if (isFullSync) preCrawled else preCrawled.take(30)
            val batches = toProcess.chunked(IMPORT_BATCH_SIZE)
            val pctPerBatch = (maxPct - basePct) / Math.max(1, batches.size)

            for ((index, batch) in batches.withIndex()) {
                // One transaction per batch: without it each row forces its own disk sync.
                database.withTransaction {
                    for (item in batch) {
                        importCatalogueRow(item)
                    }
                }
                setProgress(workDataOf("progress_msg" to "Importing $categoryName...", "progress_pct" to (basePct + ((index + 1) * pctPerBatch))))
            }
            return
        }

        val scraper = if (serverPreferences.activeServer.value.type == com.potflix.data.local.preferences.ServerType.ALIST) aListScraper else PotFlixScraper
        
        setProgress(workDataOf("progress_msg" to "Fetching $categoryName directory...", "progress_pct" to basePct))
        val rootEntries = scraper.scrapeDirectory(baseUrl)
        
        val pctPerYear = (maxPct - basePct) / Math.max(1, yearsToSync.size)
        
        // 1. Check if we have year folders like "(2024)"
        val yearFoldersFound = rootEntries.filter { it.isDirectory && yearsToSync.any { year -> it.name.contains("($year)") } }
        
        if (yearFoldersFound.isNotEmpty()) {
            for ((index, year) in yearsToSync.withIndex()) {
                val currentBasePct = basePct + (index * pctPerYear)
                setProgress(workDataOf("progress_msg" to "Syncing $categoryName ($year)...", "progress_pct" to currentBasePct))
                
                val yearFolder = rootEntries.find { it.isDirectory && it.name.contains("($year)") }
                if (yearFolder != null) {
                    val movieFolders = scraper.scrapeDirectory(yearFolder.url)
                    Log.d("SyncWorker", "Syncing $categoryName for year $year")
                    processMovieFolders(if (isFullSync) movieFolders else movieFolders.take(30), categoryName, year, scraper)
                }
            }
        } else {
            // No year folders found, assume movies are directly in the root directory (CDN Structure)
            Log.d("SyncWorker", "No year folders found, syncing $categoryName directly...")
            setProgress(workDataOf("progress_msg" to "Syncing $categoryName...", "progress_pct" to basePct))
            
            // Critical Fix: Filter movies by year to avoid infinite sync times!
            val recentMovies = rootEntries.filter { it.year?.toString() in yearsToSync }
            val moviesToSync = if (isFullSync) {
                if (recentMovies.isNotEmpty()) recentMovies else rootEntries
            } else {
                if (recentMovies.isNotEmpty()) recentMovies.take(30) else rootEntries.take(30)
            }
            
            Log.d("SyncWorker", "Found ${moviesToSync.size} movies for $categoryName")
            processMovieFolders(moviesToSync, categoryName, "", scraper)
        }
    }

    private suspend fun processMovieFolders(folders: List<com.potflix.data.remote.ScrapedEntry>, categoryName: String, defaultYear: String, scraper: com.potflix.data.remote.DirectoryScraper) {
        // Movies might be folders (FTP / some CDNs) or directly video files (sometimes)
        // Here we primarily look for folders that contain the movie, or fallback
        for (mf in folders.filter { it.isDirectory }) {
            val parsedTitle = mf.title ?: mf.name
            val parsedYear = mf.year?.toString() ?: defaultYear
            
            // Check if already in DB
            val existing = movieDao.getMovieIdByTitleAndYear(parsedTitle, parsedYear)
            if (existing == null) {
                try {
                    Log.d("SyncWorker", "New movie discovered: $parsedTitle ($parsedYear)")
                    
                    // Scrape video files
                    val contents = scraper.scrapeDirectory(mf.url)
                    val allVideos = contents.filter { it.isVideo }
                    val ftpPoster = contents.find { it.isImage }?.url
                    
                    // Fetch TMDB metadata safely
                    val tmdbMatch = try {
                        val tmdbSearch = api.searchMovie(parsedTitle, parsedYear)
                        tmdbSearch.results.firstOrNull {
                            when (categoryName) {
                                "Bollywood" -> it.originalLanguage == "hi"
                                "South Indian" -> it.originalLanguage in listOf("te", "ta", "ml", "kn")
                                "Tollywood" -> it.originalLanguage == "bn"
                                "Hollywood", "Animation", "Foreign" -> it.originalLanguage == "en" || it.originalLanguage != "hi"
                                else -> true
                            }
                        } ?: tmdbSearch.results.firstOrNull()
                    } catch (e: Exception) {
                        Log.w("SyncWorker", "Failed to fetch TMDB for $parsedTitle: ${e.message}")
                        null
                    }
                    
                    // Insert Movie
                    val movieId = movieDao.insertMovie(
                        com.potflix.data.local.entity.MovieEntity(
                            title = parsedTitle,
                            year = parsedYear,
                            posterUrl = ftpPoster ?: tmdbMatch?.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                            category = categoryName,
                            region = null,
                            tmdbId = tmdbMatch?.id?.toLong(),
                            overview = tmdbMatch?.overview,
                            rating = tmdbMatch?.voteAverage,
                            isImdbTop250 = categoryName == "IMDB Top 250"
                        )
                    )
                    
                    // Insert Videos
                    if (movieId > 0) {
                        if (allVideos.isNotEmpty()) {
                            for (v in allVideos) {
                                movieDao.insertVideo(
                                    com.potflix.data.local.entity.VideoEntity(
                                        movieId = movieId,
                                        quality = v.quality ?: mf.quality ?: "720p",
                                        url = v.url,
                                        fileName = v.name
                                    )
                                )
                            }
                        } else {
                            movieDao.insertVideo(
                                com.potflix.data.local.entity.VideoEntity(
                                    movieId = movieId,
                                    quality = mf.quality ?: "720p",
                                    url = mf.url,
                                    fileName = mf.name
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to process movie $parsedTitle", e)
                }
            }
        }
    }

    private suspend fun syncTvSeries(categoryName: String, baseUrl: String) {
        val preCrawled = getPreCrawledData(categoryName)
        if (preCrawled != null && preCrawled.isNotEmpty()) {
            Log.d("SyncWorker", "Using pre-crawled database for TV Series: $categoryName")
            setProgress(workDataOf("progress_msg" to "Importing $categoryName...", "progress_pct" to 90f))
            
            for (item in preCrawled) {
                val existingSearch = movieDao.searchMovies(item.title)
                val exists = existingSearch.any { it.movie.category == categoryName && it.movie.title.equals(item.title, ignoreCase = true) }
                
                if (!exists) {
                    val movieId = movieDao.insertMovie(
                        com.potflix.data.local.entity.MovieEntity(
                            title = item.title,
                            year = item.year ?: "",
                            posterUrl = item.posterUrl,
                            category = item.category,
                            region = "English", // Defaulting to English as fallback
                            tmdbId = item.tmdbId,
                            overview = item.overview,
                            rating = item.rating?.takeIf { it > 0.0 }
                        )
                    )
                    if (movieId > 0) {
                        for (v in item.resolvedVideos()) {
                            movieDao.insertVideo(
                                com.potflix.data.local.entity.VideoEntity(
                                    movieId = movieId,
                                    quality = v.quality,
                                    url = v.url,
                                    fileName = v.name
                                )
                            )
                        }
                    }
                }
            }
            return
        }

        val scraper = if (serverPreferences.activeServer.value.type == com.potflix.data.local.preferences.ServerType.ALIST) aListScraper else PotFlixScraper
        
        // Root level delta check
        val rootEntries = scraper.scrapeDirectory(baseUrl)
        // Recursively find series folders up to depth 2
        suspend fun crawl(entries: List<com.potflix.data.remote.ScrapedEntry>, depth: Int, parentFolderName: String = "") {
            if (depth > 2) return
            setProgress(workDataOf("progress_msg" to "Syncing TV Series...", "progress_pct" to 90f))
            for (folder in entries.filter { it.isDirectory }) {
                // If it looks like a series folder (has year or specific name structure)
                // Or if it's a structural folder like "A-L"
                val isSeasonFolder = folder.name.contains("Season", ignoreCase = true)
                val hasYear = folder.name.contains(Regex("\\(.*?(19|20)\\d{2}.*?\\)"))
                
                if (isSeasonFolder || hasYear) {
                    // If it's just "Season 1", the title should be its parent folder's name (e.g. "13 Reasons Why")
                    val seriesTitle = if (isSeasonFolder) {
                        parentFolderName.takeIf { it.isNotEmpty() } ?: folder.name
                    } else {
                        folder.name
                    }
                    processSeriesFolder(seriesTitle, folder.url, categoryName)
                } else {
                    crawl(scraper.scrapeDirectory(folder.url), depth + 1, parentFolderName = folder.name)
                }
            }
        }
        crawl(rootEntries, 0)
    }

    private suspend fun processSeriesFolder(seriesTitleRaw: String, folderUrl: String, categoryName: String) {
        val title = seriesTitleRaw.substringBefore("(").trim().takeIf { it.isNotEmpty() } ?: seriesTitleRaw
        
        // Search if exists
        val search = movieDao.searchMovies(title)
        val exists = search.any { it.movie.category == categoryName && it.movie.title.equals(title, ignoreCase = true) }
        
        if (!exists) {
            try {
                Log.d("SyncWorker", "New TV Series discovered: $title")
                
                val tmdbMatch = try {
                    val tmdbSearch = api.searchTv(title, null)
                    tmdbSearch.results.firstOrNull()
                } catch (e: Exception) {
                    Log.w("SyncWorker", "Failed to fetch TMDB for $title: ${e.message}")
                    null
                }
                
                val movieId = movieDao.insertMovie(
                    com.potflix.data.local.entity.MovieEntity(
                        title = title,
                        year = Regex("\\b(19|20)\\d{2}\\b").find(seriesTitleRaw)?.value ?: "",
                        posterUrl = tmdbMatch?.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }, 
                        category = categoryName,
                        region = "English",
                        tmdbId = tmdbMatch?.id?.toLong(),
                        overview = tmdbMatch?.overview,
                        rating = tmdbMatch?.voteAverage
                    )
                )
                if (movieId > 0) {
                    movieDao.insertVideo(
                        com.potflix.data.local.entity.VideoEntity(
                            movieId = movieId,
                            quality = "Tv & Web Series",
                            url = folderUrl,
                            fileName = title // Use the title as fallback file name
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to process series $title", e)
            }
        }
    }
}

private const val IMPORT_BATCH_SIZE = 250
private const val FLIX_7 = "http://172.16.50.7/DHAKA-FLIX-7/"
private const val FLIX_10 = "http://172.16.50.10/DHAKA-FLIX-10/"
private const val FLIX_12 = "http://172.16.50.12/DHAKA-FLIX-12/"
private const val FLIX_14 = "http://172.16.50.14/DHAKA-FLIX-14/"

private val LIVE_MOVIE_SECTIONS = listOf(
    "Hollywood" to "${FLIX_7}English%20Movies/",
    "Hollywood" to "${FLIX_14}English%20Movies%20%281080p%29/",
    "Foreign" to "${FLIX_7}Foreign%20Language%20Movies/",
    "Tollywood" to "${FLIX_7}Kolkata%20Bangla%20Movies/",
    "Bollywood" to "${FLIX_14}Hindi%20Movies/",
    "Animation" to "${FLIX_14}Animation%20Movies/",
    "Animation" to "${FLIX_14}Animation%20Movies%20%281080p%29/",
    "South Indian" to "${FLIX_14}SOUTH%20INDIAN%20MOVIES/South%20Movies/",
    "South Indian" to "${FLIX_14}SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/"
)

private val LIVE_SERIES_SECTIONS = listOf(
    "TV Series" to "${FLIX_12}TV-WEB-Series/",
    "Korean TV Series" to "${FLIX_14}KOREAN%20TV%20%26%20WEB%20Series/",
    "Anime Series" to "${FLIX_10}Anime%20%26%20Cartoon%20TV%20Series/",
    "Documentary Series" to "${FLIX_10}Documentary/"
)
