package com.potflix.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.potflix.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Where the updater is. The app shows a prompt only for [Ready]. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val version: String, val percent: Int) : UpdateState
    data class Ready(val version: String, val notes: String, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** A release on GitHub, reduced to what the updater needs. */
data class ReleaseInfo(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String, val apkSize: Long)

/**
 * Keeps the app up to date from the GitHub releases of [RELEASES_REPO]. The release workflow
 * tags each build "v3.1.N", where N is the commit count, and the version code is 400 + N.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val prefs by lazy { context.getSharedPreferences("app_updater", Context.MODE_PRIVATE) }
    private val updatesDir by lazy { File(context.cacheDir, "updates").apply { mkdirs() } }
    // Doesn't follow redirects, so the "latest release" redirect can be read.
    private val probeClient by lazy {
        OkHttpClient.Builder().followRedirects(false).connectTimeout(15, TimeUnit.SECONDS).build()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Looks for a newer release and downloads it. Automatic checks run at most once a day and
     * only download on unmetered networks; a check the user asked for always runs and downloads.
     */
    suspend fun check(manual: Boolean): UpdateState = mutex.withLock {
        withContext(Dispatchers.IO) {
            deleteInstalledUpdates()
            val current = _state.value
            if (current is UpdateState.Ready && current.apk.exists()) return@withContext current

            val now = System.currentTimeMillis()
            if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return@withContext current
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            _state.value = UpdateState.Checking
            val result = runCatching {
                val release = fetchLatestRelease()
                when {
                    release == null || release.versionCode <= BuildConfig.VERSION_CODE -> UpdateState.UpToDate
                    !manual && isMeteredNetwork() -> UpdateState.Idle // wait for Wi-Fi
                    else -> download(release)
                }
            }.getOrElse { UpdateState.Failed(it.message ?: "Couldn't reach GitHub") }
            _state.value = result
            result
        }
    }

    /** Opens Android's installer for a downloaded update, asking for install permission first if needed. */
    fun install(ready: UpdateState.Ready): InstallStep {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallStep.NeedsPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return InstallStep.Started
    }

    enum class InstallStep { Started, NeedsPermission }

    /**
     * Finds the newest release from github.com's "latest release" redirect rather than the GitHub
     * API. The API allows only 60 anonymous requests an hour per internet address, and an ISP
     * that puts many customers behind one address uses that up, so checks failed with 403.
     */
    private fun fetchLatestRelease(): ReleaseInfo? {
        val request = Request.Builder()
            .url("https://github.com/$RELEASES_REPO/releases/latest")
            .head()
            .header("User-Agent", "SAMFLIX-Android")
            .build()
        val tag = probeClient.newCall(request).execute().use { response ->
            check(response.isRedirect) { "GitHub answered ${response.code}" }
            tagFromLatestRedirect(response.header("Location").orEmpty())
        } ?: return null // no releases yet
        val versionCode = versionCodeFromTag(tag) ?: return null
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in RELEASE_ABIS } ?: return null
        return ReleaseInfo(
            versionCode = versionCode,
            versionName = tag.removePrefix("v"),
            notes = "",
            apkUrl = apkUrlFor(tag, abi),
            apkSize = -1L
        )
    }

    private fun download(release: ReleaseInfo): UpdateState {
        val target = File(updatesDir, "SAMFLIX-${release.versionCode}.apk")
        if (target.exists() && (release.apkSize < 0 || target.length() == release.apkSize)) {
            return UpdateState.Ready(release.versionName, release.notes, target)
        }
        val partial = File(updatesDir, "${target.name}.part")
        _state.value = UpdateState.Downloading(release.versionName, 0)
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = response.body ?: error("Download was empty")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = (copied * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(release.versionName, percent)
                            }
                        }
                    }
                }
            }
        }
        check(release.apkSize < 0 || partial.length() == release.apkSize) { "Download was incomplete" }
        target.delete()
        check(partial.renameTo(target)) { "Couldn't save the update" }
        return UpdateState.Ready(release.versionName, release.notes, target)
    }

    /** Removes downloaded updates that are already installed, so they don't sit in storage. */
    private fun deleteInstalledUpdates() {
        updatesDir.listFiles()?.forEach { file ->
            val code = file.name.removePrefix("SAMFLIX-").substringBefore(".").toIntOrNull()
            if (code == null || code <= BuildConfig.VERSION_CODE) file.delete()
        }
    }

    private fun isMeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    companion object {
        const val RELEASES_REPO = "mahbubvy/SAMFLIX"
        private val RELEASE_ABIS = setOf("arm64-v8a", "armeabi-v7a")

        /** ".../releases/tag/v3.1.26" → "v3.1.26"; null when there's no release yet. */
        fun tagFromLatestRedirect(location: String): String? =
            location.substringAfter("/releases/tag/", "").substringBefore('?').takeIf { it.isNotBlank() }

        /** The APK the release workflow publishes for [abi], e.g. SAMFLIX-3.1.26-arm64-v8a.apk. */
        fun apkUrlFor(tag: String, abi: String): String =
            "https://github.com/$RELEASES_REPO/releases/download/$tag/SAMFLIX-${tag.removePrefix("v")}-$abi.apk"
        private const val KEY_LAST_CHECK = "last_check"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val VERSION_CODE_BASE = 400

        /** "v3.1.57" → 457, matching app/build.gradle.kts. */
        fun versionCodeFromTag(tag: String): Int? =
            tag.removePrefix("v").split(".").takeIf { it.size == 3 && it[0] == "3" && it[1] == "1" }
                ?.get(2)?.toIntOrNull()?.let { VERSION_CODE_BASE + it }
    }
}
