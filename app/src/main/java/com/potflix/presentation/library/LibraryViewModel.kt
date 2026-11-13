package com.potflix.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.domain.model.Movie
import com.potflix.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Watch history for the Library tab. Watchlist and downloads come from their own view models. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    val history = repository.getWatchHistoryFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun removeFromHistory(movie: Movie) {
        viewModelScope.launch { repository.removeFromWatchHistory(movie.url) }
    }
}
