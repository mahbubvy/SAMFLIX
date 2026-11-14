package com.potflix.presentation.downloads

import android.app.DownloadManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.potflix.data.local.entity.LocalDownloadEntity
import com.potflix.presentation.navigation.Screen
import com.potflix.service.DownloadService
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.potflix.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    viewModel: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No downloads yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(downloads) { download ->
                    DownloadItemCard(download, navController, viewModel)
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    download: LocalDownloadEntity,
    navController: NavController,
    viewModel: DownloadsViewModel,
    label: String = download.title
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            if (download.status == DownloadService.STATUS_SUCCESSFUL && download.localUri != null) {
                navController.navigate(Screen.Player.createRoute(download.localUri, download.localUri, download.title))
            }
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            AsyncImage(
                model = download.poster ?: "https://via.placeholder.com/150",
                contentDescription = null,
                modifier = Modifier.width(70.dp).fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier.weight(1f).padding(12.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                when (download.status) {
                    DownloadService.STATUS_SUCCESSFUL -> {
                        Text("Completed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadService.STATUS_FAILED -> {
                        Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadService.STATUS_PAUSED -> {
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Paused - ${download.progress}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val speedMb = download.speedBytesPerSecond / (1024f * 1024f)
                        val speedText = String.format("%.1f MB/s", speedMb)
                        val etaText = if (download.etaSeconds > 60) "${download.etaSeconds / 60}m ${download.etaSeconds % 60}s" else "${download.etaSeconds}s"
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${download.progress}%", style = MaterialTheme.typography.bodySmall)
                            Text("$speedText • ETA: $etaText", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (download.status == DownloadService.STATUS_SUCCESSFUL) {
                    IconButton(onClick = { 
                        if (download.localUri != null) {
                            navController.navigate(Screen.Player.createRoute(download.localUri, download.localUri, download.title))
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (download.status == DownloadService.STATUS_RUNNING) {
                    IconButton(onClick = { viewModel.pauseDownload(download.downloadId) }) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_media_pause), contentDescription = "Pause")
                    }
                } else if (download.status == DownloadService.STATUS_PAUSED || download.status == DownloadService.STATUS_FAILED) {
                    IconButton(onClick = { viewModel.resumeDownload(download) }) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_media_play), contentDescription = "Resume")
                    }
                }
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteDialog) {
        val finished = download.status == DownloadService.STATUS_SUCCESSFUL
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (finished) "Delete this download?" else "Cancel this download?") },
            text = {
                Text(
                    if (finished) {
                        "\"${download.title}\" will be removed from your phone. You'll need to download it again to watch it offline."
                    } else {
                        "\"${download.title}\" will stop downloading and its progress will be lost."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteDownload(download.downloadId)
                }) {
                    Text(if (finished) "Delete" else "Cancel download", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (finished) "Keep" else "Keep downloading")
                }
            }
        )
    }
}

/** A downloaded movie or series in the Library grid: its poster, name and episode count. */
@Composable
fun DownloadGroupCard(group: DownloadGroup, onClick: () -> Unit) {
    val active = group.items.filter { it.download.status != DownloadService.STATUS_SUCCESSFUL }
    val done = group.items.size - active.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Space.xs)
            .clip(SamShapes.poster)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(SamShapes.poster).background(SamSurfaceRaised)) {
            AsyncImage(
                model = group.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (group.isSeries) {
                Text(
                    text = "${group.items.size} ep",
                    style = MaterialTheme.typography.labelMedium,
                    color = SamOnAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.s)
                        .clip(SamShapes.chip)
                        .background(SamAccent)
                        .padding(horizontal = Space.s, vertical = 2.dp)
                )
            }
            if (active.isNotEmpty()) {
                val progress = active.sumOf { it.download.progress } / (active.size * 100f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = SamAccent,
                    trackColor = SamBackground.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                )
            }
        }
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleSmall,
            color = SamText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.s)
        )
        val size = group.items.sumOf { maxOf(it.download.totalBytes, it.download.bytesDownloaded) }
        val status = when {
            active.isEmpty() -> formatSize(size)
            active.any { it.download.status == DownloadService.STATUS_RUNNING } -> "Downloading ${active.size}"
            else -> "${active.size} paused or failed"
        }
        Text(
            text = if (group.isSeries && active.isEmpty()) "$done episodes · $status" else status,
            style = MaterialTheme.typography.bodySmall,
            color = SamTextMuted,
            maxLines = 1
        )
    }
}

/** Inside a downloaded title: its episodes in order, each playable offline. */
@Composable
fun DownloadGroupDetail(
    group: DownloadGroup,
    onBack: () -> Unit,
    navController: NavController,
    viewModel: DownloadsViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xs)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to downloads", tint = SamText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, color = SamText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (group.isSeries) "${group.items.size} episodes downloaded" else "Movie",
                    style = MaterialTheme.typography.bodySmall,
                    color = SamTextMuted
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = screenEdgePadding(), vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.m),
            modifier = Modifier.fillMaxSize()
        ) {
            items(group.items, key = { it.download.downloadId }) { item ->
                DownloadItemCard(item.download, navController, viewModel, label = episodeLabel(group, item))
            }
        }
    }
}

private fun episodeLabel(group: DownloadGroup, item: DownloadedItem): String {
    if (!group.isSeries) return item.download.title
    val name = item.download.title.substringAfter(" - S", "").substringAfter(" - ", "").trim()
    val number = when {
        item.season != null && item.episode != null -> "S${item.season} · E${item.episode}"
        item.episode != null -> "Episode ${item.episode}"
        else -> null
    }
    return listOfNotNull(number, name.takeIf { it.isNotBlank() && it != "null" }).joinToString("  ")
        .ifBlank { item.download.title }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "Downloaded"
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toFloat())
    else -> "${bytes / (1L shl 20)} MB"
}
