package com.potflix.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.domain.model.Movie
import com.potflix.presentation.common.MovieCard
import com.potflix.presentation.common.SamChipRow
import com.potflix.presentation.common.SamTopBar
import com.potflix.presentation.common.SettingsAction
import com.potflix.presentation.downloads.DownloadItemCard
import com.potflix.presentation.downloads.DownloadsViewModel
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.navigation.playHero
import com.potflix.presentation.theme.SamError
import com.potflix.presentation.theme.SamSurfaceRaised
import com.potflix.presentation.theme.SamText
import com.potflix.presentation.theme.SamTextMuted
import com.potflix.presentation.theme.Space
import com.potflix.presentation.theme.screenEdgePadding
import com.potflix.presentation.watchlist.WatchlistViewModel

private enum class LibrarySection(val label: String) { History("History"), Watchlist("Watchlist"), Downloads("Downloads") }

/** The Library tab: what you've watched, what you saved, and what's on the device. */
@Composable
fun LibraryScreen(
    navController: NavController,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel(),
    downloadsViewModel: DownloadsViewModel = hiltViewModel()
) {
    var section by rememberSaveable { mutableStateOf(LibrarySection.History) }
    val history by libraryViewModel.history.collectAsState()
    val watchlist by watchlistViewModel.watchlist.collectAsState()
    val downloads by downloadsViewModel.downloads.collectAsState()
    var historyToRemove by remember { mutableStateOf<Movie?>(null) }
    val edge = screenEdgePadding()

    Column(modifier = Modifier.fillMaxSize()) {
        SamTopBar(title = "Library", actions = { SettingsAction { navController.navigate(Screen.Settings.route) } })
        SamChipRow(
            options = LibrarySection.entries.map { it.label },
            selectedIndex = section.ordinal,
            onSelect = { section = LibrarySection.entries[it] },
            modifier = Modifier.padding(horizontal = edge, vertical = Space.s)
        )
        when (section) {
            LibrarySection.History -> PosterGrid(
                movies = history,
                emptyText = "Nothing watched yet. What you play shows up here so you can pick up where you left off.",
                onOpen = { movie -> navController.playHero(movie) {} },
                onLongPress = { historyToRemove = it }
            )
            LibrarySection.Watchlist -> PosterGrid(
                movies = watchlist,
                emptyText = "Your watchlist is empty. Add titles from their detail page to find them here.",
                onOpen = { movie -> navController.navigate(Screen.Detail.createRoute(movie)) }
            )
            LibrarySection.Downloads -> if (downloads.isEmpty()) {
                EmptyNote("No downloads yet. Downloaded videos play without a connection to the server.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = edge, vertical = Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(downloads, key = { it.downloadId }) { download ->
                        DownloadItemCard(download, navController, downloadsViewModel)
                    }
                }
            }
        }
    }

    historyToRemove?.let { movie ->
        AlertDialog(
            onDismissRequest = { historyToRemove = null },
            title = { Text("Remove from history?", color = SamText) },
            text = { Text("\"${movie.title}\" and its saved position will be removed.", color = SamTextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.removeFromHistory(movie)
                    historyToRemove = null
                }) { Text("Remove", color = SamError) }
            },
            dismissButton = {
                TextButton(onClick = { historyToRemove = null }) { Text("Keep", color = SamText) }
            },
            containerColor = SamSurfaceRaised
        )
    }
}

@Composable
private fun PosterGrid(
    movies: List<Movie>,
    emptyText: String,
    onOpen: (Movie) -> Unit,
    onLongPress: ((Movie) -> Unit)? = null
) {
    if (movies.isEmpty()) {
        EmptyNote(emptyText)
        return
    }
    val edge = screenEdgePadding()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(horizontal = edge - Space.xs, vertical = Space.s),
        modifier = Modifier.fillMaxSize()
    ) {
        items(movies) { movie ->
            MovieCard(
                movie = movie,
                onClick = { onOpen(movie) },
                onLongClick = onLongPress?.let { { it(movie) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.xxl),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = SamTextMuted, textAlign = TextAlign.Center)
    }
}

