package com.potflix.presentation.player

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.SurfaceHolder
import androidx.compose.runtime.mutableStateOf
import com.potflix.R
import com.potflix.util.PlayerTrack
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils

private const val LARGE_CACHE_RAM_BYTES = 4L * 1024 * 1024 * 1024

/** Renders video and holds the options mpv must receive before it starts. */
class MpvSurfaceView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {
    var onSurfaceCreated: (() -> Unit)? = null
    var onSurfaceResized: (() -> Unit)? = null
    var subtitleScale: Double = 1.0

    override fun initOptions() {
        MPVLib.setOptionString("profile", "fast")
        setVo("gpu")
        MPVLib.setOptionString("hwdec", "mediacodec,no")
        MPVLib.setOptionString("hwdec-codecs", "all")
        MPVLib.setOptionString("msg-level", "all=warn")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")

        // Exact seeks land where the viewer asked; dropped frames keep them quick.
        MPVLib.setOptionString("hr-seek", "yes")
        MPVLib.setOptionString("hr-seek-framedrop", "yes")

        // Video kept behind the playhead is what makes short jumps back instant.
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val largeCache = memory.totalMem >= LARGE_CACHE_RAM_BYTES
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", if (largeCache) "150MiB" else "64MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", if (largeCache) "75MiB" else "32MiB")
        MPVLib.setOptionString("network-timeout", "20")

        // The app chooses tracks itself from the viewer's language settings.
        MPVLib.setOptionString("alang", "")
        MPVLib.setOptionString("slang", "")
        MPVLib.setOptionString("sub-auto", "no")
        MPVLib.setOptionString("sub-scale", subtitleScale.toString())
    }

    override fun postInitOptions() = Unit

    override fun observeProperties() {
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("idle-active", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NONE)
        MPVLib.observeProperty("aid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        onSurfaceCreated?.invoke()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.surfaceChanged(holder, format, width, height)
        onSurfaceResized?.invoke()
    }
}

/**
 * The only route to mpv. libmpv is one native instance per process and aborts the whole app
 * if called after it is destroyed, so every call here first checks [active].
 */
class MpvController(context: Context, subtitleScale: Double) : MPVLib.EventObserver {
    val view: MpvSurfaceView = LayoutInflater.from(context)
        .inflate(R.layout.mpv_surface, null) as MpvSurfaceView

    val isPlaying = mutableStateOf(true)
    val isBuffering = mutableStateOf(true)
    val currentTimeMs = mutableStateOf(0L)
    val durationMs = mutableStateOf(0L)
    val cachedTimeMs = mutableStateOf(0L)
    val hardwareDecoding = mutableStateOf(true)
    val fileLoaded = mutableStateOf(false)
    val playbackEnded = mutableStateOf(false)
    val audioTracks = mutableStateOf<List<PlayerTrack>>(emptyList())
    val subtitleTracks = mutableStateOf<List<PlayerTrack>>(emptyList())
    val selectedAudio = mutableStateOf(-1)
    val selectedSubtitle = mutableStateOf(-1)

    var onLoadFailed: (() -> Unit)? = null

    private val mainThread = Handler(Looper.getMainLooper())
    private var active = false
    private var pendingLoad: Array<String>? = null
    private var seekTargetMs: Long? = null
    private var waitingForPicture = true
    private var pausedForCache = false
    private var fileStarted = false

    init {
        // A previous screen that is still closing must let go of the native instance first.
        current?.release()
        current = this
        Utils.copyAssets(context)
        view.subtitleScale = subtitleScale
        view.onSurfaceCreated = {
            val load = pendingLoad
            if (load != null) {
                pendingLoad = null
                issueLoad(load)
            } else {
                repaintIfPaused()
            }
        }
        view.onSurfaceResized = ::repaintIfPaused
        MPVLib.addObserver(this)
        view.initialize(context.filesDir.path, context.cacheDir.path)
        // BaseMPVView asks mpv to exit after one file, which would strand the next episode.
        MPVLib.setPropertyString("idle", "yes")
        active = true
    }

    var time: Long
        get() {
            seekTargetMs?.let { return it }
            if (!active) return currentTimeMs.value
            return MPVLib.getPropertyDouble("time-pos")?.let { (it * 1000).toLong() } ?: currentTimeMs.value
        }
        set(value) {
            if (!active || durationMs.value <= 0L) return
            // Further skips must add to this target, not to the position mpv has not reached yet.
            seekTargetMs = value
            currentTimeMs.value = value
            MPVLib.command("seek", (value / 1000.0).toString(), "absolute+exact")
        }

    val length: Long get() = durationMs.value

    /** Follows a finger on the seek gesture: fast keyframe jumps, with an exact [time] at the end. */
    fun previewSeek(positionMs: Long) {
        if (!active || durationMs.value <= 0L) return
        seekTargetMs = positionMs
        currentTimeMs.value = positionMs
        MPVLib.command("seek", (positionMs / 1000.0).toString(), "absolute+keyframes")
    }

    fun setZoom(scale: Float) {
        if (active) MPVLib.setPropertyDouble("video-zoom", kotlin.math.log2(scale.toDouble()))
    }

    fun setHardwareDecoding(enabled: Boolean) {
        if (!active) return
        hardwareDecoding.value = enabled
        MPVLib.setPropertyString("hwdec", if (enabled) "mediacodec,no" else "no")
    }

    var audioTrack: Int
        get() = selectedAudio.value
        set(value) {
            if (!active) return
            selectedAudio.value = value
            MPVLib.setPropertyInt("aid", value)
        }

    var spuTrack: Int
        get() = selectedSubtitle.value
        set(value) {
            if (!active) return
            selectedSubtitle.value = value
            if (value == -1) MPVLib.setPropertyString("sid", "no") else MPVLib.setPropertyInt("sid", value)
        }

    fun load(url: String, startMs: Long) {
        if (!active) return
        fileLoaded.value = false
        playbackEnded.value = false
        currentTimeMs.value = 0L
        durationMs.value = 0L
        cachedTimeMs.value = 0L
        seekTargetMs = null
        waitingForPicture = true
        updateBuffering()
        MPVLib.setPropertyBoolean("pause", false)
        val command = if (startMs > 0L) {
            arrayOf("loadfile", url, "replace", "-1", "start=${startMs / 1000}")
        } else {
            arrayOf("loadfile", url, "replace")
        }
        if (view.holder.surface.isValid) issueLoad(command) else pendingLoad = command
    }

    private fun issueLoad(command: Array<String>) {
        if (active) MPVLib.command(*command)
    }

    fun play() {
        if (!active) return
        // keep-open holds the last frame, so playing from there has to start the video over.
        if (playbackEnded.value) time = 0L
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun pause() {
        if (active) MPVLib.setPropertyBoolean("pause", true)
    }

    fun setSpeed(speed: Float) {
        if (active) MPVLib.setPropertyDouble("speed", speed.toDouble())
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        if (active) MPVLib.setPropertyDouble("sub-delay", delayMs / 1000.0)
    }

    fun setAudioDelayMs(delayMs: Long) {
        if (active) MPVLib.setPropertyDouble("audio-delay", delayMs / 1000.0)
    }

    fun setSubtitleScale(scale: Double) {
        if (active) MPVLib.setPropertyDouble("sub-scale", scale)
    }

    fun addSubtitle(path: String, title: String, select: Boolean) {
        if (active) MPVLib.command("sub-add", path, if (select) "select" else "auto", title)
    }

    fun setFitMode(mode: String) {
        if (!active) return
        MPVLib.setPropertyDouble("video-zoom", 0.0)
        MPVLib.setPropertyDouble("panscan", if (mode == "Crop") 1.0 else 0.0)
        MPVLib.setPropertyBoolean("keepaspect", mode != "Stretch")
        MPVLib.setPropertyString("video-unscaled", if (mode == "Original") "yes" else "no")
        MPVLib.setPropertyString(
            "video-aspect-override",
            when (mode) {
                "16:9" -> "16:9"
                "4:3" -> "4:3"
                else -> "no"
            }
        )
    }

    fun release() {
        if (!active) return
        active = false
        pendingLoad = null
        view.onSurfaceCreated = null
        view.onSurfaceResized = null
        MPVLib.removeObserver(this)
        view.destroy()
        if (current === this) current = null
    }

    /** While paused mpv draws nothing, so a changed surface would otherwise keep a stale frame. */
    private fun repaintIfPaused() {
        if (active && fileLoaded.value && !isPlaying.value) {
            MPVLib.command("seek", "0", "relative+exact")
        }
    }

    private fun refreshTracks() {
        if (!active) return
        val audio = mutableListOf<PlayerTrack>()
        val subtitles = mutableListOf<PlayerTrack>()
        var audioSelection = -1
        var subtitleSelection = -1
        for (index in 0 until (MPVLib.getPropertyInt("track-list/count") ?: 0)) {
            val id = MPVLib.getPropertyInt("track-list/$index/id") ?: continue
            val type = MPVLib.getPropertyString("track-list/$index/type")
            if (type != "audio" && type != "sub") continue
            val selected = MPVLib.getPropertyBoolean("track-list/$index/selected") == true
            val track = PlayerTrack(id, trackName(index, id))
            if (type == "audio") {
                audio += track
                if (selected) audioSelection = id
            } else {
                subtitles += track
                if (selected) subtitleSelection = id
            }
        }
        audioTracks.value = audio
        subtitleTracks.value = subtitles
        selectedAudio.value = audioSelection
        selectedSubtitle.value = subtitleSelection
    }

    private fun trackName(index: Int, id: Int): String {
        val title = MPVLib.getPropertyString("track-list/$index/title")?.takeIf { it.isNotBlank() }
        val language = MPVLib.getPropertyString("track-list/$index/lang")?.takeIf { it.isNotBlank() }
        val label = title ?: "Track $id"
        return if (language != null) "$label ($language)" else label
    }

    private fun updateBuffering() {
        isBuffering.value = waitingForPicture || pausedForCache
    }

    private fun onMain(block: () -> Unit) {
        // mpv reports from its own thread; state and further calls belong on the main thread.
        mainThread.post { if (active) block() }
    }

    override fun eventProperty(property: String) {
        if (property == "track-list") onMain { refreshTracks() }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> onMain { if (seekTargetMs == null) currentTimeMs.value = value * 1000L }
            "demuxer-cache-time" -> onMain { cachedTimeMs.value = value * 1000L }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        onMain {
            when (property) {
                "pause" -> isPlaying.value = !value
                "eof-reached" -> playbackEnded.value = value
                "paused-for-cache" -> {
                    pausedForCache = value
                    updateBuffering()
                }
                "idle-active" -> if (value && fileStarted && !fileLoaded.value) {
                    // mpv fell idle after starting a file it never opened.
                    fileStarted = false
                    waitingForPicture = false
                    updateBuffering()
                    isPlaying.value = false
                    onLoadFailed?.invoke()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (property == "aid" || property == "sid") onMain { refreshTracks() }
    }

    override fun eventProperty(property: String, value: Double) {
        if (property == "duration") onMain { durationMs.value = (value * 1000).toLong() }
    }

    override fun eventProperty(property: String, value: MPVNode) = Unit

    override fun event(eventId: Int, data: MPVNode) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> onMain { fileStarted = true }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> onMain {
                refreshTracks()
                fileLoaded.value = true
            }
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> onMain {
                seekTargetMs = null
                waitingForPicture = false
                updateBuffering()
            }
        }
    }

    private companion object {
        var current: MpvController? = null
    }
}
