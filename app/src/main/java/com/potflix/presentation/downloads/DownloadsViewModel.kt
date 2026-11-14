package com.potflix.presentation.downloads

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.local.dao.LocalDownloadDao
import com.potflix.data.local.entity.LocalDownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import com.potflix.service.DownloadService
import com.potflix.util.DownloadFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val localDownloadDao: LocalDownloadDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val downloads = localDownloadDao.getAllDownloads().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Downloads grouped by movie or series, episodes in order. */
    val groups = localDownloadDao.getAllDownloads().map(::groupDownloads).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        moveOldDownloadsToSharedFolder()
    }

    /**
     * Downloads made before files went to Movies/SAMFLIX sit in the app's private folder, where
     * file managers can't see them. Move the finished ones over.
     */
    fun moveOldDownloadsToSharedFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!DownloadFiles.canUseSharedFolder(context)) return@launch
            localDownloadDao.getAllDownloads().first()
                .filter { it.status == DownloadService.STATUS_SUCCESSFUL }
                .forEach { download ->
                    val path = download.localUri ?: return@forEach
                    if (!DownloadFiles.isInAppFolder(context, path)) return@forEach
                    val series = download.seriesTitle ?: groupDownloads(listOf(download)).first().takeIf { it.isSeries }?.title
                    val moved = DownloadFiles.moveToSharedFolder(context, File(path), download.title, download.streamUrl, series)
                        ?: return@forEach
                    localDownloadDao.update(download.copy(localUri = moved.absolutePath, seriesTitle = series))
                }
        }
    }

    fun deleteDownload(downloadId: Long) {
        val intent = android.content.Intent(context, com.potflix.service.DownloadService::class.java).apply {
            action = com.potflix.service.DownloadService.ACTION_CANCEL
            putExtra(com.potflix.service.DownloadService.EXTRA_ID, downloadId)
        }
        context.startService(intent)
    }

    fun pauseDownload(downloadId: Long) {
        val intent = android.content.Intent(context, com.potflix.service.DownloadService::class.java).apply {
            action = com.potflix.service.DownloadService.ACTION_PAUSE
            putExtra(com.potflix.service.DownloadService.EXTRA_ID, downloadId)
        }
        context.startService(intent)
    }

    fun resumeDownload(download: com.potflix.data.local.entity.LocalDownloadEntity) {
        val intent = android.content.Intent(context, com.potflix.service.DownloadService::class.java).apply {
            action = com.potflix.service.DownloadService.ACTION_RESUME
            putExtra(com.potflix.service.DownloadService.EXTRA_ID, download.downloadId)
            putExtra(com.potflix.service.DownloadService.EXTRA_URL, download.streamUrl)
            putExtra(com.potflix.service.DownloadService.EXTRA_TITLE, download.title)
            putExtra(com.potflix.service.DownloadService.EXTRA_POSTER, download.poster)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
