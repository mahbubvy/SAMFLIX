package com.potflix.presentation.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One request to play a video. Each is a separate object, so asking again always counts as new. */
private class PlayRequest(
    val movieUrl: String,
    val streamUrl: String,
    val title: String,
    val playbackPosition: Long
)

private fun Intent.toPlayRequest(): PlayRequest? {
    val streamUrl = getStringExtra("streamUrl") ?: return null
    return PlayRequest(
        movieUrl = getStringExtra("movieUrl") ?: "",
        streamUrl = streamUrl,
        title = getStringExtra("title") ?: "Video",
        playbackPosition = getLongExtra("playbackPosition", 0L)
    )
}

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val _isInPipMode = mutableStateOf(false)
    private var playRequest by mutableStateOf<PlayRequest?>(null)

    @javax.inject.Inject
    lateinit var movieRepository: com.potflix.domain.repository.MovieRepository

    @javax.inject.Inject
    lateinit var serverPreferences: com.potflix.data.local.preferences.ServerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Auto-Orientation: Force landscape mode upon opening, regardless of system orientation lock
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Immersive Mode
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        playRequest = intent.toPlayRequest() ?: return

        setContent {
            PotFlixTheme {
                val request = playRequest ?: return@PotFlixTheme
                // The player uses a blue accent; the rest of the app keeps its own colours.
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = PlayerAccentBlue)) {
                    // A new request starts a fresh player, which saves the old video's progress on the way out.
                    key(request) {
                        VideoPlayer(
                            movieUrl = request.movieUrl,
                            streamUrl = request.streamUrl,
                            title = request.title,
                            playbackPosition = request.playbackPosition,
                            isPipMode = _isInPipMode.value,
                            movieRepository = movieRepository,
                            serverPreferences = serverPreferences
                        )
                    }
                }
            }
        }
    }

    /** singleTask brings this activity back, in place of a new one, when a video is picked during picture-in-picture. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = intent.toPlayRequest() ?: return
        // Picking the video already in the small window only brings it back, at its current position.
        if (isInPictureInPictureMode && request.streamUrl == playRequest?.streamUrl) return
        playRequest = request
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPictureInPicture()
    }

    fun enterPictureInPicture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }
}

@Composable
fun VideoPlayer(
    movieUrl: String = "",
    streamUrl: String,
    title: String,
    playbackPosition: Long = 0L,
    isPipMode: Boolean = false,
    movieRepository: com.potflix.domain.repository.MovieRepository? = null,
    serverPreferences: com.potflix.data.local.preferences.ServerPreferences? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var serverSubtitle by remember { mutableStateOf<java.io.File?>(null) }

    var seasons by remember { mutableStateOf<List<com.potflix.domain.model.Season>>(emptyList()) }
    var currentStreamUrl by remember { mutableStateOf(streamUrl) }
    var currentTitle by remember { mutableStateOf(title) }
    var showEpisodesPanel by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(movieUrl) {
        if (movieUrl.isNotEmpty() && movieRepository != null) {
            val result = movieRepository.getSeriesEpisodes(movieUrl)
            result.getOrNull()?.let {
                seasons = it
            }
        }
    }

    val currentStreamUrlState by rememberUpdatedState(currentStreamUrl)

    val playerPrefs = remember { context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE) }
    var subtitleSizePercent by remember { mutableStateOf(playerPrefs.getInt("subtitle_size", 100)) }

    val mediaPlayer = remember { MpvController(context, subtitleSizePercent / 100.0) }
    val audioTracks by mediaPlayer.audioTracks
    val spuTracks by mediaPlayer.subtitleTracks
    var selectedAudioTrack by mediaPlayer.selectedAudio
    var selectedSpuTrack by mediaPlayer.selectedSubtitle

    val playEpisode: (String, String) -> Unit = { newStreamUrl, newTitle ->
        // Save current progress before switching
        movieRepository?.let { repo ->
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                repo.updateWatchProgress(movieUrl, currentStreamUrlState, mediaPlayer.time, mediaPlayer.length)
            }
        }
        currentStreamUrl = newStreamUrl
        currentTitle = newTitle
    }

    // Fetch Subtitles
    LaunchedEffect(currentStreamUrl) {
        serverSubtitle = null
        serverSubtitle = withContext(Dispatchers.IO) { downloadServerSubtitle(context, currentStreamUrl) }
    }

    val subtitleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val cacheDir = java.io.File(context.cacheDir, "subtitles").apply { mkdirs() }
                    val originalName = getFileNameFromUri(context, uri) ?: "loaded_subtitle.srt"
                    val sanitizedName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val localSubFile = java.io.File(cacheDir, "${System.currentTimeMillis()}_$sanitizedName")

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        localSubFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        mediaPlayer.addSubtitle(localSubFile.path, originalName, select = true)
                        android.widget.Toast.makeText(context, "Subtitle active: $originalName", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerActivity", "Failed to load subtitle file", e)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to load subtitle: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(currentStreamUrl, loadAttempt) {
        loadFailed = false
        mediaPlayer.load(
            url = playableUrl(currentStreamUrl),
            startMs = if (currentStreamUrl == streamUrl) playbackPosition else 0L
        )
    }

    val fileLoaded by mediaPlayer.fileLoaded

    fun selectPreferredSubtitle() {
        val preferred = serverPreferences?.getPreferredSubtitleLanguage() ?: "off"
        if (preferred == "off") {
            mediaPlayer.spuTrack = -1
        } else {
            com.potflix.util.LanguageUtils.findMatchingTrack(spuTracks, preferred, isSubtitle = true)
                ?.let { mediaPlayer.spuTrack = it.id }
        }
    }

    LaunchedEffect(fileLoaded) {
        if (fileLoaded) {
            val preferredAudio = serverPreferences?.getPreferredAudioLanguage() ?: "en"
            if (preferredAudio != "auto") {
                com.potflix.util.LanguageUtils.findMatchingTrack(audioTracks, preferredAudio, isSubtitle = false)
                    ?.let { mediaPlayer.audioTrack = it.id }
            }
            selectPreferredSubtitle()
        }
    }

    LaunchedEffect(serverSubtitle, fileLoaded) {
        val subtitle = serverSubtitle
        if (fileLoaded && subtitle != null) {
            mediaPlayer.addSubtitle(subtitle.path, subtitle.name, select = false)
        }
    }

    val isPlaying by mediaPlayer.isPlaying
    val isBuffering by mediaPlayer.isBuffering
    var currentTime by mediaPlayer.currentTimeMs
    val totalDuration by mediaPlayer.durationMs
    var isControlsVisible by remember { mutableStateOf(true) }

    val activity = context as? ComponentActivity
    var isPortrait by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isUnlockPromptVisible by remember { mutableStateOf(false) }

    var isDraggingTimeline by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }
    var dragFraction by remember { mutableStateOf(0f) }

    var resizeMode by remember {
        mutableStateOf(playerPrefs.getString("fit_mode", null)?.takeIf { it in FIT_MODES } ?: FIT_MODES.first())
    }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var audioDelayMs by remember { mutableStateOf(0L) }
    var openSheet by remember { mutableStateOf(PlayerSheet.None) }
    var interactionTick by remember { mutableStateOf(0) }

    LaunchedEffect(resizeMode) {
        mediaPlayer.setFitMode(resizeMode)
    }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.onLoadFailed = {
            isControlsVisible = false
            isScreenLocked = false
            openSheet = PlayerSheet.None
            showEpisodesPanel = false
            loadFailed = true
        }
        onDispose {
            val savedTime = mediaPlayer.time
            val savedLength = mediaPlayer.length
            val savedStreamUrl = currentStreamUrlState
            movieRepository?.let { repo ->
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    repo.updateWatchProgress(movieUrl, savedStreamUrl, savedTime, savedLength)
                }
            }
            mediaPlayer.onLoadFailed = null
            mediaPlayer.release()
        }
    }

    val accessibilityManager = remember {
        context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    }

    // Hide the controls 4 seconds after the last touch. Buffering shows its own spinner,
    // so it must neither reopen the controls nor hold them on screen. TalkBack users
    // explore the screen by touch and need the controls to stay put while they do.
    LaunchedEffect(isControlsVisible, isPlaying, isDraggingTimeline, isScreenLocked, openSheet, interactionTick) {
        if (isControlsVisible && isPlaying && !isPipMode && !isDraggingTimeline && !isScreenLocked &&
            openSheet == PlayerSheet.None && !accessibilityManager.isTouchExplorationEnabled
        ) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    // Auto-hide unlock prompt when screen is locked after 2.5 seconds
    LaunchedEffect(isUnlockPromptVisible) {
        if (isUnlockPromptVisible) {
            delay(2500L)
            isUnlockPromptVisible = false
        }
    }

    LaunchedEffect(isPipMode) {
        if (isPipMode) {
            isControlsVisible = false
        }
    }

    // Back closes whatever is on top first. A locked screen stays locked until slid open.
    BackHandler(enabled = isScreenLocked || openSheet != PlayerSheet.None || showEpisodesPanel) {
        when {
            isScreenLocked -> isUnlockPromptVisible = true
            openSheet != PlayerSheet.None -> openSheet = PlayerSheet.None
            else -> showEpisodesPanel = false
        }
    }

    val playerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    // Picture-in-picture only reaches this point once its window is closed.
                    if (isPlaying) {
                        mediaPlayer.pause()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Watches every touch on its way to the controls without consuming it.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        interactionTick++
                    }
                }
            }
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter,
                        Key.Enter -> {
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                            true
                        }
                        Key.DirectionLeft -> {
                            mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0)
                            isControlsVisible = true
                            true
                        }
                        Key.DirectionRight -> {
                            mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
                            isControlsVisible = true
                            true
                        }
                        Key.DirectionUp -> {
                            isControlsVisible = true
                            true
                        }
                        Key.DirectionDown -> {
                            isControlsVisible = false
                            true
                        }
                        Key.MediaPlayPause -> {
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                            true
                        }
                        Key.MediaPlay -> {
                            mediaPlayer.play()
                            true
                        }
                        Key.MediaPause -> {
                            mediaPlayer.pause()
                            true
                        }
                        Key.MediaFastForward -> {
                            mediaPlayer.time = (mediaPlayer.time + 30000).coerceAtMost(mediaPlayer.length)
                            true
                        }
                        Key.MediaRewind -> {
                            mediaPlayer.time = (mediaPlayer.time - 30000).coerceAtLeast(0)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { mediaPlayer.view },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isPipMode) {
                        if (isScreenLocked) {
                            isUnlockPromptVisible = !isUnlockPromptVisible
                        } else {
                            isControlsVisible = !isControlsVisible
                            if (showEpisodesPanel) showEpisodesPanel = false
                        }
                    }
                }
        )

        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }
        var volumeLevel by remember { mutableStateOf(0f) }

        val window = (context as? ComponentActivity)?.window
        var currentBrightness by remember { mutableStateOf(startingBrightness(context, window)) }

        var showVolumeIndicator by remember { mutableStateOf(false) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }

        // Horizontal seek state
        var seekDragAccumulator by remember { mutableStateOf(0f) }
        var showSeekIndicator by remember { mutableStateOf(false) }
        var seekDelta by remember { mutableStateOf(0L) }
        var isSeekDragging by remember { mutableStateOf(false) }

        var doubleTapSide by remember { mutableStateOf(0) }
        var doubleTapSeconds by remember { mutableStateOf(0) }
        var isHoldingForSpeed by remember { mutableStateOf(false) }
        var showZoomMessage by remember { mutableStateOf(false) }

        // Every gesture update restarts this, so indicators stay during a swipe and leave right after it.
        var gestureTick by remember { mutableStateOf(0) }
        LaunchedEffect(gestureTick) {
            if (gestureTick > 0) {
                delay(GESTURE_INDICATOR_LINGER_MS)
                showVolumeIndicator = false
                showBrightnessIndicator = false
                showZoomMessage = false
                doubleTapSide = 0
                doubleTapSeconds = 0
                if (!isSeekDragging) {
                    showSeekIndicator = false
                    seekDelta = 0L
                }
            }
        }

        var zoomScale by remember { mutableStateOf(1f) }
        var seekStartMs by remember { mutableStateOf(0L) }
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

        fun changeBrightness(change: Float) {
            currentBrightness = (currentBrightness + change).coerceIn(MIN_BRIGHTNESS, 1f)
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = currentBrightness
                w.attributes = attrs
            }
            showBrightnessIndicator = true
        }

        fun changeVolume(change: Float) {
            if (!showVolumeIndicator && maxVolume > 0) {
                // The phone's volume buttons may have moved it since the last swipe.
                volumeLevel = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
            }
            volumeLevel = (volumeLevel + change).coerceIn(0f, 1f)
            val newVolume = kotlin.math.round(volumeLevel * maxVolume).toInt()
            if (newVolume != currentVolume) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
                currentVolume = newVolume
            }
            showVolumeIndicator = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Two fingers zoom. Runs first so the taps and swipes below never see them.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (!isScreenLocked && event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    zoomScale = (zoomScale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    mediaPlayer.setZoom(zoomScale)
                                    showZoomMessage = true
                                    gestureTick++
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (isHoldingForSpeed) {
                                isHoldingForSpeed = false
                                mediaPlayer.setSpeed(playbackSpeed)
                            }
                        },
                        onLongPress = {
                            if (!isScreenLocked && isPlaying) {
                                isHoldingForSpeed = true
                                isControlsVisible = false
                                mediaPlayer.setSpeed(HOLD_SPEED)
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            }
                        },
                        onDoubleTap = { position ->
                            if (isScreenLocked) {
                                isUnlockPromptVisible = true
                            } else {
                                val side = when {
                                    position.x < size.width * DOUBLE_TAP_SIDE_FRACTION -> -1
                                    position.x > size.width * (1f - DOUBLE_TAP_SIDE_FRACTION) -> 1
                                    else -> 0
                                }
                                if (side == 0) {
                                    if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                                } else {
                                    mediaPlayer.time = (mediaPlayer.time + side * DOUBLE_TAP_SEEK_MS).coerceIn(0, mediaPlayer.length)
                                    // Repeated double-taps add up, and the video stays uncovered.
                                    doubleTapSeconds = if (doubleTapSide == side) {
                                        doubleTapSeconds + (DOUBLE_TAP_SEEK_MS / 1000).toInt()
                                    } else {
                                        (DOUBLE_TAP_SEEK_MS / 1000).toInt()
                                    }
                                    doubleTapSide = side
                                    gestureTick++
                                }
                            }
                        },
                        onTap = {
                            if (isScreenLocked) {
                                isUnlockPromptVisible = !isUnlockPromptVisible
                            } else if (showEpisodesPanel || openSheet != PlayerSheet.None) {
                                showEpisodesPanel = false
                                openSheet = PlayerSheet.None
                            } else {
                                isControlsVisible = !isControlsVisible
                            }
                        }
                    )
                }
                .pointerInput(isScreenLocked) {
                    if (!isScreenLocked) {
                        var totalDx = 0f
                        var totalDy = 0f
                        var isVertical: Boolean? = null
                        var startedAtEdge = false
                        var startedOnLeft = false
                        fun finishDrag(applySeek: Boolean) {
                            if (isSeekDragging) {
                                val target = if (applySeek) seekStartMs + seekDelta else seekStartMs
                                mediaPlayer.time = target.coerceIn(0, mediaPlayer.length)
                            }
                            isSeekDragging = false
                            isVertical = null
                            gestureTick++
                        }
                        detectDragGestures(
                            onDragStart = { start ->
                                totalDx = 0f
                                totalDy = 0f
                                isVertical = null
                                seekDragAccumulator = 0f
                                startedOnLeft = start.x < size.width / 2
                                // Screen edges belong to the system bars and gesture navigation.
                                startedAtEdge = start.y < size.height * SCREEN_EDGE_FRACTION ||
                                    start.y > size.height * (1f - SCREEN_EDGE_FRACTION)
                            },
                            onDragEnd = { finishDrag(applySeek = true) },
                            onDragCancel = { finishDrag(applySeek = false) },
                            onDrag = { change, dragAmount ->
                                if (!startedAtEdge) {
                                    change.consume()
                                    totalDx += dragAmount.x
                                    totalDy += dragAmount.y
                                    if (isVertical == null && (kotlin.math.abs(totalDx) > 24f || kotlin.math.abs(totalDy) > 24f)) {
                                        isVertical = kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)
                                        if (isVertical == false) seekStartMs = mediaPlayer.time
                                    }
                                    if (isVertical == true) {
                                        val level = -dragAmount.y / size.height * VERTICAL_SWIPE_GAIN
                                        if (startedOnLeft) changeBrightness(level) else changeVolume(level)
                                        gestureTick++
                                    } else if (isVertical == false) {
                                        isSeekDragging = true
                                        seekDragAccumulator += dragAmount.x
                                        // A full screen width is two minutes, in whole seconds.
                                        seekDelta = (seekDragAccumulator / size.width * FULL_WIDTH_SEEK_MS).toLong() / 1000L * 1000L
                                        mediaPlayer.previewSeek((seekStartMs + seekDelta).coerceIn(0, mediaPlayer.length))
                                        showSeekIndicator = true
                                        gestureTick++
                                    }
                                }
                            }
                        )
                    }
                }
        )

        if (doubleTapSide != 0 && !isPipMode) {
            DoubleTapSeekOval(
                forward = doubleTapSide > 0,
                seconds = doubleTapSeconds,
                modifier = Modifier.align(if (doubleTapSide > 0) Alignment.CenterEnd else Alignment.CenterStart)
            )
        }

        // Each slider sits opposite its swiping thumb, so the thumb never covers it.
        if (showBrightnessIndicator && !isScreenLocked) {
            LevelSlider(
                icon = Icons.Outlined.BrightnessMedium,
                percent = (currentBrightness * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
            )
        }
        if (showVolumeIndicator && !isScreenLocked) {
            LevelSlider(
                icon = Icons.Outlined.VolumeUp,
                percent = if (maxVolume > 0) currentVolume * 100 / maxVolume else 0,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp)
            )
        }

        val gestureMessage = when {
            isHoldingForSpeed -> "${HOLD_SPEED.toInt()}x"
            showSeekIndicator && isSeekDragging -> {
                val sign = if (seekDelta >= 0) "+" else "-"
                "${formatTime((seekStartMs + seekDelta).coerceIn(0, totalDuration))}  [$sign${kotlin.math.abs(seekDelta) / 1000}s]"
            }
            showZoomMessage -> "Zoom ${String.format(java.util.Locale.US, "%.1f", zoomScale)}x"
            else -> null
        }
        if (gestureMessage != null && !isPipMode) {
            GestureMessage(
                text = gestureMessage,
                showFastForward = isHoldingForSpeed,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
            )
        }

        val currentEpisodeInfo = remember(seasons, currentStreamUrl) {
            if (seasons.isEmpty()) null
            else {
                var matchPair: Pair<com.potflix.domain.model.Season, com.potflix.domain.model.Episode>? = null
                for (season in seasons) {
                    val ep = season.episodes.find {
                        it.url == currentStreamUrl ||
                        currentStreamUrl.endsWith(it.url.substringAfterLast("/")) ||
                        it.url.substringAfterLast("/") == currentStreamUrl.substringAfterLast("/")
                    }
                    if (ep != null) {
                        matchPair = Pair(season, ep)
                        break
                    }
                }
                matchPair
            }
        }

        // Center spinner when buffering and controls are hidden
        if (isBuffering && !isControlsVisible && !isPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.5.dp
                )
            }
        }

        AnimatedVisibility(
            visible = isScreenLocked && isUnlockPromptVisible && !isPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            SlideToUnlock(onUnlock = {
                isScreenLocked = false
                isUnlockPromptVisible = false
                isControlsVisible = true
            })
        }

        val allEpisodes = remember(seasons) { seasons.flatMap { it.episodes } }
        val exactEpisodeIndex = allEpisodes.indexOfFirst { it.url == currentStreamUrl }
        val episodeIndex = if (exactEpisodeIndex >= 0) {
            exactEpisodeIndex
        } else {
            currentEpisodeInfo?.let { allEpisodes.indexOf(it.second) } ?: -1
        }
        val previousEpisode = allEpisodes.getOrNull(episodeIndex - 1).takeIf { episodeIndex > 0 }
        val nextEpisode = allEpisodes.getOrNull(episodeIndex + 1).takeIf { episodeIndex >= 0 }
        val cachedTime by mediaPlayer.cachedTimeMs

        val playbackEnded by mediaPlayer.playbackEnded
        var nextEpisodeCancelled by remember { mutableStateOf(false) }
        var nextEpisodeCountdown by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(playbackEnded, nextEpisode, nextEpisodeCancelled) {
            nextEpisodeCountdown = null
            if (!playbackEnded) {
                nextEpisodeCancelled = false
                return@LaunchedEffect
            }
            val next = nextEpisode
            if (next == null || nextEpisodeCancelled) {
                isControlsVisible = !isScreenLocked
                return@LaunchedEffect
            }
            for (seconds in NEXT_EPISODE_COUNTDOWN_SECONDS downTo 1) {
                nextEpisodeCountdown = seconds
                delay(1000L)
            }
            nextEpisodeCountdown = null
            // Someone who closed the player window should not have the next episode start unseen.
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                playEpisode(next.url, next.title)
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked && !isPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val baseSeriesName = remember(title) {
                title.substringBefore(" • ").substringBefore(" - Season").substringBefore(" - Episode").trim()
            }
            val episodeLabel = currentEpisodeInfo?.let { (season, episode) ->
                val seasonNumber = season.number.takeIf { it > 0 } ?: Regex("\\d+").find(season.name)?.value?.toIntOrNull() ?: 1
                // A number shared by several files can't tell them apart, so the list position is used instead.
                val parsedNumber = episode.number?.takeIf { number ->
                    number > 0 && season.episodes.count { it.number == number } == 1
                }
                val episodeNumber = parsedNumber ?: (season.episodes.indexOf(episode) + 1).takeIf { it > 0 }
                val code = if (episodeNumber != null) {
                    "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
                } else {
                    season.name
                }
                val isRealName = episode.title.isNotBlank() && !episode.title.startsWith("Episode ", ignoreCase = true) &&
                    !episode.title.contains(".mkv", ignoreCase = true) && !episode.title.contains(".mp4", ignoreCase = true)
                if (isRealName) "$code: ${episode.title}" else code
            }
            PlayerControlsOverlay(
                title = if (currentEpisodeInfo != null) baseSeriesName else currentTitle,
                subtitle = episodeLabel,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentTimeMs = currentTime,
                durationMs = totalDuration,
                cachedTimeMs = cachedTime,
                scrubFraction = if (isDraggingTimeline) dragFraction else null,
                isPortrait = isPortrait,
                speed = playbackSpeed,
                hasAudioChoices = audioTracks.isNotEmpty(),
                subtitlesOn = selectedSpuTrack != -1,
                actions = PlayerControlsActions(
                    onBack = { (context as? ComponentActivity)?.finish() },
                    onPlayPause = { if (isPlaying) mediaPlayer.pause() else mediaPlayer.play() },
                    onPrevious = previousEpisode?.let { episode -> { playEpisode(episode.url, episode.title) } },
                    onNext = nextEpisode?.let { episode -> { playEpisode(episode.url, episode.title) } },
                    onOpenSheet = {
                        openSheet = it
                        isControlsVisible = false
                    },
                    onEpisodes = if (seasons.isNotEmpty()) {
                        {
                            showEpisodesPanel = true
                            isControlsVisible = false
                        }
                    } else {
                        null
                    },
                    onLock = {
                        isScreenLocked = true
                        isControlsVisible = false
                        isUnlockPromptVisible = true
                    },
                    onToggleOrientation = {
                        isPortrait = !isPortrait
                        activity?.requestedOrientation = if (isPortrait) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onPictureInPicture = { (context as? PlayerActivity)?.enterPictureInPicture() },
                    onScrub = { fraction ->
                        isDraggingTimeline = true
                        dragFraction = fraction
                        dragPositionMs = (fraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                    },
                    onScrubEnd = {
                        mediaPlayer.time = dragPositionMs
                        isDraggingTimeline = false
                    }
                )
            )
        }

        // Next Episode Button overlay
        val isNextEpisodeVisible = nextEpisode != null && nextEpisodeCountdown == null &&
            totalDuration > 0 && (totalDuration - currentTime <= 120_000L)
        AnimatedVisibility(
            visible = isNextEpisodeVisible && !isPipMode,
            enter = fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 116.dp, end = 24.dp)
        ) {
            Button(
                onClick = { nextEpisode?.let { playEpisode(it.url, it.title) } },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Next Episode", color = Color.White)
            }
        }

        nextEpisodeCountdown?.let { seconds ->
            if (!isPipMode && !isScreenLocked) {
                NextEpisodeCountdown(
                    seconds = seconds,
                    onPlayNow = { nextEpisode?.let { playEpisode(it.url, it.title) } },
                    onCancel = { nextEpisodeCancelled = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 116.dp, end = 24.dp)
                )
            }
        }

        PlayerSheets(
            sheet = openSheet,
            audioTracks = audioTracks,
            selectedAudio = selectedAudioTrack,
            onSelectAudio = { mediaPlayer.audioTrack = it },
            audioDelayMs = audioDelayMs,
            onAudioDelayChange = {
                audioDelayMs = it
                mediaPlayer.setAudioDelayMs(it)
            },
            subtitleTracks = spuTracks,
            selectedSubtitle = selectedSpuTrack,
            onSelectSubtitle = { mediaPlayer.spuTrack = it },
            onAddSubtitleFile = {
                openSheet = PlayerSheet.None
                subtitleLauncher.launch("*/*")
            },
            subtitleDelayMs = subtitleDelayMs,
            onSubtitleDelayChange = {
                subtitleDelayMs = it
                mediaPlayer.setSubtitleDelayMs(it)
            },
            subtitleSizePercent = subtitleSizePercent,
            onSubtitleSizeChange = {
                subtitleSizePercent = it
                playerPrefs.edit().putInt("subtitle_size", it).apply()
                mediaPlayer.setSubtitleScale(it / 100.0)
            },
            speed = playbackSpeed,
            onSpeedChange = {
                playbackSpeed = it
                mediaPlayer.setSpeed(it)
            },
            fitMode = resizeMode,
            onFitModeChange = {
                resizeMode = it
                playerPrefs.edit().putString("fit_mode", it).apply()
            },
            hardwareDecoding = mediaPlayer.hardwareDecoding.value,
            onHardwareDecodingChange = { mediaPlayer.setHardwareDecoding(it) },
            onOpenSheet = {
                openSheet = it
                if (it != PlayerSheet.None) isControlsVisible = false
            }
        )

        // Episodes Side Panel
        AnimatedVisibility(
            visible = showEpisodesPanel && !isPipMode,
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.35f) // Take up 35% of the screen width
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Episodes", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        IconButton(
                            onClick = { showEpisodesPanel = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        seasons.forEach { season ->
                            item {
                                Text(
                                    text = season.name,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(season.episodes.size) { index ->
                                val episode = season.episodes[index]
                                val isSelected = episode.url == currentStreamUrl
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playEpisode(episode.url, episode.title)
                                            showEpisodesPanel = false
                                        }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = episode.title,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (loadFailed && !isPipMode) {
            PlayerLoadError(
                title = currentTitle,
                isLocalFile = currentStreamUrl.startsWith("/"),
                onRetry = { loadAttempt++ },
                onBack = { (context as? ComponentActivity)?.finish() }
            )
        }
    }
}

/** Catalogue links can hold raw spaces, which a server rejects once mpv sends them as they are. */
private fun playableUrl(url: String): String =
    if (url.startsWith("/")) url else Uri.encode(url, ":/?#[]@!$&'()*+,;=%-._~")

/**
 * Fetches the subtitle beside a video to a local file, because mpv's add-subtitle call
 * blocks until the file is read and a local file keeps that from stalling the screen.
 */
private fun downloadServerSubtitle(context: android.content.Context, streamUrl: String): java.io.File? {
    if (streamUrl.startsWith("/")) return null
    return try {
        val folderUrl = streamUrl.substringBeforeLast("/") + "/"
        val links = org.jsoup.Jsoup.connect(folderUrl).maxBodySize(0).get().select("a[href\$=.srt]")
            .map { it.absUrl("href") }
        val videoName = fileNameOf(streamUrl).substringBeforeLast(".")
        val subtitleUrl = links.firstOrNull { fileNameOf(it).startsWith(videoName) } ?: links.firstOrNull()
        subtitleUrl?.let { url ->
            val body = org.jsoup.Jsoup.connect(url).ignoreContentType(true).maxBodySize(0).execute().bodyAsBytes()
            val folder = java.io.File(context.cacheDir, "subtitles").apply { mkdirs() }
            java.io.File(folder, "server_${url.hashCode()}.srt").apply { writeBytes(body) }
        }
    } catch (e: Exception) {
        android.util.Log.w("PlayerActivity", "No subtitle from server", e)
        null
    }
}

private fun fileNameOf(url: String): String =
    java.net.URLDecoder.decode(url.substringAfterLast("/").replace("+", "%2B"), "UTF-8")

private const val GESTURE_INDICATOR_LINGER_MS = 800L
private const val NEXT_EPISODE_COUNTDOWN_SECONDS = 5
private const val HOLD_SPEED = 2f
private const val DOUBLE_TAP_SIDE_FRACTION = 0.35f
private const val DOUBLE_TAP_SEEK_MS = 3_000L
private val PlayerAccentBlue = Color(0xFF2F8CFF)
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f
private const val SCREEN_EDGE_FRACTION = 0.08f
private const val VERTICAL_SWIPE_GAIN = 1.2f
private const val FULL_WIDTH_SEEK_MS = 120_000f
private const val MIN_BRIGHTNESS = 0.01f

/**
 * The window reports -1 until something overrides it, meaning "use the system level".
 * Starting a swipe from that value would clamp to zero and black out the screen.
 */
private fun startingBrightness(context: android.content.Context, window: android.view.Window?): Float {
    val override = window?.attributes?.screenBrightness ?: -1f
    if (override >= 0f) return override
    val system = try {
        android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Exception) {
        128
    }
    return (system / 255f).coerceIn(MIN_BRIGHTNESS, 1f)
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

