package com.potflix.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.potflix.presentation.movies.MoviesScreen
import com.potflix.presentation.tv.TvSeriesScreen

/**
 * The Home tab. Movies and Series used to be tabs of their own; now they are filters here,
 * and the chips in the header switch between the three views.
 */
@Composable
fun HomeTabScreen(navController: NavController) {
    var filter by rememberSaveable { mutableStateOf(CatalogueFilter.All) }
    val onFilterChange: (CatalogueFilter) -> Unit = { filter = it }
    when (filter) {
        CatalogueFilter.All -> HomeScreen(navController, filter = filter, onFilterChange = onFilterChange)
        CatalogueFilter.Movies -> MoviesScreen(navController, filter = filter, onFilterChange = onFilterChange)
        CatalogueFilter.Series -> TvSeriesScreen(navController, filter = filter, onFilterChange = onFilterChange)
    }
}
