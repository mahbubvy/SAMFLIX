package com.potflix.presentation.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val serverPreferences: com.potflix.data.local.preferences.ServerPreferences,
    private val firebaseSyncManager: com.potflix.data.remote.FirebaseSyncManager,
    private val appUpdater: com.potflix.update.AppUpdater
) : ViewModel() {

    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val isSyncing = MutableStateFlow(false)
    val syncProgress = MutableStateFlow("Starting sync...")
    
    private val _lastSyncTime = MutableStateFlow("Never")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private var wasSyncing = false

    val activeServer = serverPreferences.activeServer
    
    val currentUserEmail = firebaseSyncManager.currentUserEmail

    val dailyNotificationEnabled = serverPreferences.dailyNotificationEnabled

    fun setDailyNotificationEnabled(enabled: Boolean) {
        serverPreferences.setDailyNotificationEnabled(enabled)
        if (enabled) {
            com.potflix.worker.DailyNotificationScheduler.scheduleNextDailyNotification(application)
            _toastMessage.value = "Daily recommendations enabled"
        } else {
            com.potflix.worker.DailyNotificationScheduler.cancelDailyNotification(application)
            _toastMessage.value = "Daily recommendations disabled"
        }
    }

    val preferredAudioLanguage = serverPreferences.preferredAudioLanguage
    val preferredSubtitleLanguage = serverPreferences.preferredSubtitleLanguage

    fun setPreferredAudioLanguage(langCode: String) {
        serverPreferences.setPreferredAudioLanguage(langCode)
        _toastMessage.value = "Audio set to: ${com.potflix.util.LanguageUtils.getAudioLanguageDisplayName(langCode)}"
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        serverPreferences.setPreferredSubtitleLanguage(langCode)
        _toastMessage.value = "Subtitles set to: ${com.potflix.util.LanguageUtils.getSubtitleLanguageDisplayName(langCode)}"
    }

    val customTmdbApiKey = serverPreferences.customTmdbApiKey

    fun setCustomTmdbApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        serverPreferences.setCustomTmdbApiKey(trimmed)
        if (trimmed.isBlank()) {
            _toastMessage.value = "Reset to default TMDB API key"
        } else {
            _toastMessage.value = "Custom TMDB API key saved"
        }
    }

    init {
        calculateCacheSize()
        fetchLastSyncTime()
        observeWorkManager()
    }
    
    private fun observeWorkManager() {
        androidx.work.WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData("ManualSync")
            .observeForever { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observeForever
                val workInfo = workInfos[0]
                
                isSyncing.value = workInfo.state == androidx.work.WorkInfo.State.RUNNING || 
                                  workInfo.state == androidx.work.WorkInfo.State.ENQUEUED
                                  
                if (workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                    syncProgress.value = "Scanning servers for new content..."
                }
                
                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    syncProgress.value = "Sync complete"
                    if (wasSyncing) {
                        _toastMessage.value = "Database Sync Complete!"
                        // Update last sync time
                        val prefs = application.getSharedPreferences("potflix_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
                        fetchLastSyncTime()
                    }
                }
                wasSyncing = isSyncing.value
            }
    }
    
    fun fetchLastSyncTime() {
        val prefs = application.getSharedPreferences("potflix_prefs", Context.MODE_PRIVATE)
        val time = prefs.getLong("last_sync_time", 0L)
        if (time == 0L) {
            _lastSyncTime.value = "Never"
        } else {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
            _lastSyncTime.value = sdf.format(java.util.Date(time))
        }
    }
    
    fun syncDatabase() {
        val inputData = androidx.work.workDataOf(
            "isFullSync" to true,
            "isInitialLoad" to false
        )
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.potflix.worker.SyncWorker>()
            .setInputData(inputData)
            .build()
        androidx.work.WorkManager.getInstance(application)
            .enqueueUniqueWork("ManualSync", androidx.work.ExistingWorkPolicy.REPLACE, workRequest)
    }

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()

    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    fun setActiveServer(server: com.potflix.data.local.preferences.ServerConfig) {
        if (server.id == activeServer.value.id) {
            _toastMessage.value = "${server.name} is already selected."
            return
        }
        _toastMessage.value = "Testing connection to ${server.name}..."
        viewModelScope.launch(Dispatchers.IO) {
            val isReachable = try {
                val urlStr = server.baseUrl.trimEnd('/') + if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) "/api/fs/list" else "/"
                val url = java.net.URL(urlStr)
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.write("{\"path\":\"/\",\"password\":\"\",\"page\":1,\"per_page\":10}".toByteArray())
                } else {
                    connection.requestMethod = "HEAD"
                }
                
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val code = connection.responseCode
                code in 200..299 || code == 405 || code == 403 
            } catch (e: Exception) {
                false
            }
            
            withContext(Dispatchers.Main) {
                if (!isReachable) {
                    _toastMessage.value = "Warning: ${server.name} is unreachable. Server switch cancelled."
                    return@withContext
                }
                serverPreferences.setActiveServer(server)
                syncDatabase() // Re-sync when server is changed
                _showRestartDialog.value = true
            }
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            firebaseSyncManager.logout()
            _toastMessage.value = "Logged out successfully"
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = getDirSize(application.cacheDir)
            val coilCacheSize = getDirSize(File(application.cacheDir, "image_cache"))
            val formattedSize = formatSize(size + coilCacheSize)
            withContext(Dispatchers.Main) {
                _cacheSize.value = formattedSize
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingCache.value = true
            
            // Delete cache dir
            deleteDir(application.cacheDir)
            
            // Re-calculate
            calculateCacheSize()
            
            withContext(Dispatchers.Main) {
                _isClearingCache.value = false
                _toastMessage.value = "Cache cleared successfully"
            }
        }
    }

    fun exportDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = application.getDatabasePath("potflix_db")
                if (!dbFile.exists()) {
                    withContext(Dispatchers.Main) { _toastMessage.value = "Database file not found!" }
                    return@launch
                }
                
                // Copy to public Downloads directory
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, "potflix_db")
                
                dbFile.copyTo(destFile, overwrite = true)
                
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Database exported to Downloads folder!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Export failed: ${e.message}"
                }
            }
        }
    }

    /** Checks GitHub for a newer SAMFLIX. A found update downloads, then the app-wide prompt offers to install it. */
    fun checkForUpdates() {
        _toastMessage.value = "Checking for updates…"
        viewModelScope.launch {
            val message = when (val result = appUpdater.check(manual = true)) {
                is com.potflix.update.UpdateState.UpToDate ->
                    "You're on the latest version (${com.potflix.BuildConfig.VERSION_NAME})."
                is com.potflix.update.UpdateState.Ready -> null // the update prompt takes over
                is com.potflix.update.UpdateState.Failed -> "Couldn't check for updates: ${result.message}"
                else -> null
            }
            message?.let { _toastMessage.value = it }
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        } else if (dir.exists() && dir.isFile) {
            size += dir.length()
        }
        return size
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
        }
        return dir?.delete() ?: false
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
