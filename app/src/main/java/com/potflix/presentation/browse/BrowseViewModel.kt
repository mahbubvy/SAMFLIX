package com.potflix.presentation.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.local.dao.MovieDao
import com.potflix.data.local.preferences.ServerPreferences
import com.potflix.data.remote.PotFlixScraper
import com.potflix.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Loaded(val items: List<BrowseItem>) : BrowseUiState
    data object Unreachable : BrowseUiState
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val movieDao: MovieDao,
    private val serverPreferences: ServerPreferences
) : ViewModel() {
    private val folderUrl: String? = savedStateHandle["url"]
    private val isSeries: Boolean = savedStateHandle["series"] ?: false

    private val _state = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private val _posters = MutableStateFlow<Map<String, String>>(emptyMap())
    val posters: StateFlow<Map<String, String>> = _posters.asStateFlow()

    private val requestedPosters = mutableSetOf<String>()
    private val posterSlots = Semaphore(MAX_POSTER_REQUESTS)

    init {
        load()
    }

    fun load() {
        val url = folderUrl ?: return
        _state.value = BrowseUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                BrowseUiState.Loaded(BrowseRules.toItems(PotFlixScraper.fetchDirectory(url)))
            } catch (e: Exception) {
                BrowseUiState.Unreachable
            }
        }
    }

    /** Each title folder holds its own poster image; read it when the card first appears. */
    fun requestPoster(item: BrowseItem) {
        if (!requestedPosters.add(item.url)) return
        viewModelScope.launch {
            posterSlots.withPermit {
                val poster = PotFlixScraper.scrapeDirectory(item.url).firstOrNull { it.isImage }?.url
                if (poster != null) {
                    _posters.update { it + (item.url to poster) }
                }
            }
        }
    }

    /** Builds the details page's movie, reusing the catalogue's data and resume state if it has this title. */
    suspend fun toMovie(item: BrowseItem): Movie {
        val isTv = isSeries || item.name.contains("(TV", ignoreCase = true)
        val type = if (isTv) "tv" else "movie"
        val known = item.year?.let { year ->
            movieDao.getMovieWithDetailsByTitleAndYear(item.title, year.toString())
        }?.toDomainModel(serverPreferences.activeServer.value)

        if (known != null) {
            // Keep the folder the user picked, such as 720p rather than the catalogue's best quality.
            val sameFolder = known.videos?.firstOrNull { it.url.startsWith(item.url) }
            return known.copy(
                url = sameFolder?.url ?: item.url,
                type = if (isTv) "tv" else known.type,
                quality = item.quality ?: known.quality,
                poster = known.poster ?: _posters.value[item.url]
            )
        }
        return Movie(
            title = item.title,
            url = item.url,
            year = item.year,
            quality = item.quality,
            type = type,
            poster = _posters.value[item.url],
            releaseDate = item.year?.toString()
        )
    }

    private companion object {
        const val MAX_POSTER_REQUESTS = 4
    }
}
