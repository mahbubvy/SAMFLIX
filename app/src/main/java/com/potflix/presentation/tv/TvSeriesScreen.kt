package com.potflix.presentation.tv

import com.potflix.presentation.home.CatalogueFilter
import com.potflix.presentation.home.CatalogueHeader
import com.potflix.presentation.home.CatalogueHeaderHeight
import com.potflix.presentation.theme.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.R
import com.potflix.presentation.home.components.CategoryRow
import com.potflix.presentation.home.components.HeroBanner
import com.potflix.presentation.home.components.Top10Row
import com.potflix.presentation.common.shimmerEffect
import com.potflix.presentation.common.HomeScreenSkeleton
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.navigation.playHero
import com.potflix.presentation.common.LibraryEmptyState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSeriesScreen(
    navController: NavController,
    filter: CatalogueFilter = CatalogueFilter.All,
    onFilterChange: (CatalogueFilter) -> Unit = {},
    viewModel: TvSeriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val distinctCategories = remember(categories) { categories.distinctBy { it.id } }
    val trendingSeries by viewModel.trendingTv.collectAsState()
    val categorySeries by viewModel.categoryTv.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isHeroInWatchlist by viewModel.isHeroInWatchlist.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            pullToRefreshState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SamBackground)
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        Crossfade(
            targetState = (isLoading && categories.isEmpty()),
            animationSpec = tween(400),
            label = "TvSeriesScreenCrossfade"
        ) { loadingState ->
            if (loadingState) {
                HomeScreenSkeleton()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 90.dp, bottom = 100.dp)
                ) {
                    // Hero Banner
                if (trendingSeries.isNotEmpty()) {
                    item {
                        val heroMovie = trendingSeries.first()
                        HeroBanner(
                            movie = heroMovie,
                            onPlayClick = {
                                navController.playHero(heroMovie, viewModel::onHeroPlaybackStarted)
                            },
                            onInfoClick = {
                                navController.navigate(Screen.Detail.createRoute(heroMovie))
                            },
                            onMyListClick = {
                                viewModel.toggleHeroWatchlist()
                            },
                            isMyList = isHeroInWatchlist
                        )
                    }
                }

                // Top 10 Series Row
                if (trendingSeries.size > 1) {
                    item {
                        Top10Row(
                            title = "Trending Now",
                            movies = trendingSeries.drop(1),
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            }
                        )
                    }
                }

                // Category Rows
                itemsIndexed(
                    items = distinctCategories,
                    key = { index, category -> "tv_cat_${category.id}_$index" },
                    contentType = { _, _ -> "categoryRow" }
                ) { _, category ->
                    val seriesList = categorySeries[category.id] ?: emptyList()
                    if (seriesList.isNotEmpty()) {
                        CategoryRow(
                            category = category,
                            movies = seriesList,
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            },
                            onSeeAllClick = {
                                navController.navigate(Screen.CategoryDetail.createRoute(category.id, category.name, "category_tv"))
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(90.dp)) // Extra padding for bottom bar
                }
            }
        }
    }

        if (!isLoading && trendingSeries.isEmpty() && distinctCategories.isEmpty()) {
            LibraryEmptyState(onReload = viewModel::refresh)
        }

        CatalogueHeader(
            filter = filter,
            onFilterChange = onFilterChange,
            onOpenSettings = { navController.navigate(Screen.Settings.route) },
            syncMessage = null,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (pullToRefreshState.verticalOffset > 0f || pullToRefreshState.isRefreshing) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = CatalogueHeaderHeight),
                containerColor = SamSurface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}


