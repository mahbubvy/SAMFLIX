package com.potflix.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.theme.PotFlixCard
import com.potflix.presentation.theme.PotFlixDark
import com.potflix.presentation.theme.PotFlixRed
import kotlinx.coroutines.launch

private val MutedText = Color.White.copy(alpha = 0.6f)

/** The tab's first page: the server's sections, grouped like the DhakaFlix home page. */
@Composable
fun BrowseScreen(navController: NavController) {
    val groups = remember { BrowseRules.sections.groupBy { it.group } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PotFlixDark)
            .statusBarsPadding()
    ) {
        Text(
            text = "Browse",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (group, sections) ->
                item(key = "header-$group") {
                    Text(
                        text = group,
                        color = MutedText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                items(sections, key = { it.url }) { section ->
                    FolderRow(name = section.name, icon = Icons.Default.Folder) {
                        navController.navigate(
                            Screen.BrowseFolder.createRoute(section.url, section.name, section.isSeries)
                        )
                    }
                }
            }
        }
    }
}

/** Any folder below a section: years, index folders, then a poster grid of titles. */
@Composable
fun BrowseFolderScreen(
    navController: NavController,
    title: String,
    isSeries: Boolean,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val posters by viewModel.posters.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var isFiltering by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    BackHandler(enabled = isFiltering) {
        isFiltering = false
        query = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PotFlixDark)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                isFiltering = !isFiltering
                if (!isFiltering) query = ""
            }) {
                Icon(
                    imageVector = if (isFiltering) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isFiltering) "Close filter" else "Filter this folder",
                    tint = Color.White
                )
            }
        }

        if (isFiltering) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Filter this folder") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = PotFlixRed,
                    cursorColor = PotFlixRed
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when (val current = state) {
            BrowseUiState.Loading -> CenteredBox { CircularProgressIndicator(color = PotFlixRed) }

            BrowseUiState.Unreachable -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not reach the server.", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Check that you're on your ISP's connection.",
                        color = MutedText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(
                        onClick = viewModel::load,
                        colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed)
                    ) { Text("Retry") }
                }
            }

            is BrowseUiState.Loaded -> {
                val visible = remember(current.items, query) {
                    if (query.isBlank()) current.items
                    else current.items.filter { it.name.contains(query.trim(), ignoreCase = true) }
                }
                if (visible.isEmpty()) {
                    CenteredBox {
                        Text(
                            text = if (query.isBlank()) "This folder is empty." else "Nothing matches \"$query\".",
                            color = MutedText
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = visible,
                            key = { it.url },
                            span = { item ->
                                val isCard = item.kind == BrowseKind.TITLE
                                GridItemSpan(if (isCard) 1 else maxLineSpan)
                            }
                        ) { item ->
                            when (item.kind) {
                                BrowseKind.TITLE -> {
                                    LaunchedEffect(item.url) { viewModel.requestPoster(item) }
                                    PosterCard(item = item, poster = posters[item.url]) {
                                        scope.launch {
                                            val movie = viewModel.toMovie(item)
                                            navController.navigate(Screen.Detail.createRoute(movie))
                                        }
                                    }
                                }

                                BrowseKind.VIDEO -> FolderRow(name = item.name, icon = Icons.Default.PlayCircle) {
                                    scope.launch {
                                        val movie = viewModel.toMovie(item)
                                        navController.navigate(Screen.Detail.createRoute(movie.copy(type = "movie")))
                                    }
                                }

                                BrowseKind.YEAR, BrowseKind.FOLDER -> FolderRow(
                                    name = item.name,
                                    icon = if (item.kind == BrowseKind.YEAR) Icons.Default.CalendarMonth else Icons.Default.Folder
                                ) {
                                    navController.navigate(
                                        Screen.BrowseFolder.createRoute(item.url, item.name, isSeries)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun FolderRow(name: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PotFlixCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PotFlixRed, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MutedText)
    }
}

@Composable
private fun PosterCard(item: BrowseItem, poster: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(PotFlixCard)
        ) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = MutedText, modifier = Modifier.size(32.dp))
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (item.quality != null) {
                Text(
                    text = item.quality,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        item.year?.let {
            Text(text = it.toString(), color = MutedText, fontSize = 11.sp)
        }
    }
}
