package com.potflix.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.potflix.worker.SyncWorker

private const val SYNC_WORK_NAME = "ManualSync"

/**
 * Shown when a catalogue screen has nothing to list. The catalogue is a local copy of the
 * server, so an empty screen means the copy never loaded, and trying again has to sync it.
 */
@Composable
fun LibraryEmptyState(onReload: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val workManager = remember { WorkManager.getInstance(context) }
    val latestOnReload by rememberUpdatedState(onReload)
    var isSyncing by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val syncWork = workManager.getWorkInfosForUniqueWorkLiveData(SYNC_WORK_NAME)
        val observer = Observer<List<WorkInfo>> { infos ->
            val state = infos.firstOrNull()?.state
            val syncingNow = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
            if (isSyncing && !syncingNow) latestOnReload()
            isSyncing = syncingNow
        }
        syncWork.observe(lifecycleOwner, observer)
        onDispose { syncWork.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isSyncing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Loading your library…", color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                Text("Nothing to show yet", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "The library couldn't load. Make sure you're connected to SamOnline, then try again.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val request = OneTimeWorkRequestBuilder<SyncWorker>()
                            .setInputData(workDataOf("isFullSync" to true, "isInitialLoad" to false))
                            .build()
                        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Try again", color = Color.White)
                }
            }
        }
    }
}
