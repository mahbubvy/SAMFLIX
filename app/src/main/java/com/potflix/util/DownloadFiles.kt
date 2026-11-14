package com.potflix.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Where downloaded videos live. They go to the phone's shared Movies folder
 * (Movies/SAMFLIX, with a folder per series) so file managers, galleries and other players
 * can see them, and they survive uninstalling the app. If shared storage can't be written
 * (old Android without the storage permission), they stay in the app's own folder.
 */
object DownloadFiles {
    private const val FOLDER = "SAMFLIX"
    private val UNSAFE = Regex("""[\\/:*?"<>|]""")

    /** Android 11+ lets an app write its own videos into Movies without a permission. */
    fun canUseSharedFolder(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** True when this Android version needs the storage permission to save into Movies. */
    fun needsStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !canUseSharedFolder(context)

    fun rootFolder(context: Context): File =
        if (canUseSharedFolder(context)) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER)
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), FOLDER)
        }

    /** Movies/SAMFLIX/<title>.<ext>, or Movies/SAMFLIX/<series>/<title>.<ext> for an episode. */
    fun fileFor(context: Context, title: String, streamUrl: String, seriesTitle: String?): File {
        var folder = rootFolder(context)
        if (!seriesTitle.isNullOrBlank()) folder = File(folder, safe(seriesTitle))
        folder.mkdirs()
        return File(folder, "${safe(title)}.${extensionOf(streamUrl)}")
    }

    /** Folder of the app's older downloads, which were kept out of sight of other apps. */
    fun isInAppFolder(context: Context, path: String): Boolean {
        val appDir = context.getExternalFilesDir(null)?.absolutePath ?: return false
        return path.startsWith(appDir)
    }

    /** Lets galleries and file managers see the file straight away. */
    fun announce(context: Context, file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    /** Moves an older download into the shared folder. Returns the new file, or null if it couldn't. */
    fun moveToSharedFolder(context: Context, from: File, title: String, streamUrl: String, seriesTitle: String?): File? {
        if (!from.exists() || !canUseSharedFolder(context)) return null
        val target = fileFor(context, title, streamUrl, seriesTitle)
        if (target.absolutePath == from.absolutePath) return null
        return runCatching {
            if (!from.renameTo(target)) {
                from.inputStream().use { input -> target.outputStream().use { input.copyTo(it, 1 shl 20) } }
                if (target.length() != from.length()) error("copy incomplete")
                from.delete()
            }
            announce(context, target)
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    private fun safe(name: String): String = name.replace(UNSAFE, "_").trim().trimEnd('.').ifBlank { "video" }.take(150)

    internal fun extensionOf(streamUrl: String): String {
        val name = Uri.decode(streamUrl.substringBefore('?').substringAfterLast('/'))
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext in VIDEO_EXTENSIONS) ext else "mp4"
    }

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "wmv", "flv", "3gp", "mpg", "mpeg")
}
