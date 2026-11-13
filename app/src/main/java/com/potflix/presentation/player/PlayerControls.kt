package com.potflix.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potflix.util.PlayerTrack
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal val FIT_MODES = listOf("Fit", "Crop", "Stretch", "16:9", "4:3", "Original")
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
private val SUBTITLE_SIZES = listOf(75 to "Small", 100 to "Normal", 150 to "Large", 200 to "Huge")
private const val TIMING_STEP_MS = 250L

private val DockColor = Color(0xB31C1B1F)
private val SheetColor = Color(0xF71A1A20)
private val DockShape = RoundedCornerShape(16.dp)

enum class PlayerSheet { None, Audio, Subtitles, Speed, Fit, More }

internal fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/** Everything the on-screen controls show and can trigger. */
class PlayerControlsActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: (() -> Unit)?,
    val onNext: (() -> Unit)?,
    val onOpenSheet: (PlayerSheet) -> Unit,
    val onEpisodes: (() -> Unit)?,
    val onLock: () -> Unit,
    val onToggleOrientation: () -> Unit,
    val onPictureInPicture: () -> Unit,
    val onScrub: (Float) -> Unit,
    val onScrubEnd: () -> Unit
)

@Composable
fun PlayerControlsOverlay(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentTimeMs: Long,
    durationMs: Long,
    cachedTimeMs: Long,
    scrubFraction: Float?,
    isPortrait: Boolean,
    speed: Float,
    hasAudioChoices: Boolean,
    subtitlesOn: Boolean,
    actions: PlayerControlsActions
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Soft shading at the edges only, so the picture stays visible behind the controls.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The title side takes all the free space, which pushes the button dock to the right edge.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            ControlsDock {
                DockButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = actions.onBack)
                Column(modifier = Modifier.padding(start = 4.dp, end = 14.dp)) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            }
            ControlsDock {
                if (hasAudioChoices) {
                    DockButton(Icons.Outlined.Audiotrack, "Audio tracks") { actions.onOpenSheet(PlayerSheet.Audio) }
                }
                DockButton(Icons.Outlined.Subtitles, "Subtitles", active = subtitlesOn) {
                    actions.onOpenSheet(PlayerSheet.Subtitles)
                }
                actions.onEpisodes?.let { DockButton(Icons.Outlined.VideoLibrary, "Episodes", onClick = it) }
                DockButton(Icons.Outlined.MoreVert, "More options") { actions.onOpenSheet(PlayerSheet.More) }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actions.onPrevious != null || actions.onNext != null) {
                CenterButton(Icons.Filled.SkipPrevious, "Previous episode", 52, 30, actions.onPrevious)
            }
            if (isBuffering) {
                Box(
                    modifier = Modifier.size(72.dp).background(DockColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.5.dp
                    )
                }
            } else {
                CenterButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    72,
                    44,
                    actions.onPlayPause
                )
            }
            if (actions.onPrevious != null || actions.onNext != null) {
                CenterButton(Icons.Filled.SkipNext, "Next episode", 52, 30, actions.onNext)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlsDock {
                    DockButton(Icons.Outlined.LockOpen, "Lock controls", onClick = actions.onLock)
                    DockButton(
                        Icons.Outlined.ScreenRotation,
                        if (isPortrait) "Switch to landscape" else "Switch to portrait",
                        active = isPortrait,
                        onClick = actions.onToggleOrientation
                    )
                    if (speed == 1f) {
                        DockButton(Icons.Outlined.Speed, "Playback speed") { actions.onOpenSheet(PlayerSheet.Speed) }
                    } else {
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { actions.onOpenSheet(PlayerSheet.Speed) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                speedLabel(speed),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                ControlsDock {
                    DockButton(Icons.Outlined.PictureInPictureAlt, "Picture in picture", onClick = actions.onPictureInPicture)
                    DockButton(Icons.Outlined.AspectRatio, "Screen fit") { actions.onOpenSheet(PlayerSheet.Fit) }
                }
            }
            SeekbarRow(
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                cachedTimeMs = cachedTimeMs,
                scrubFraction = scrubFraction,
                onScrub = actions.onScrub,
                onScrubEnd = actions.onScrubEnd
            )
        }
    }
}

@Composable
private fun ControlsDock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(DockShape)
            .background(DockColor)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun DockButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CenterButton(
    icon: ImageVector,
    description: String,
    sizeDp: Int,
    iconDp: Int,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(DockColor)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (onClick != null) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(iconDp.dp)
        )
    }
}

@Composable
private fun SeekbarRow(
    isPlaying: Boolean,
    currentTimeMs: Long,
    durationMs: Long,
    cachedTimeMs: Long,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit
) {
    var showRemaining by remember { mutableStateOf(false) }
    val shownTime = if (scrubFraction != null) (scrubFraction * durationMs).toLong() else currentTimeMs
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatTime(shownTime),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        WavySeekbar(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            fraction = scrubFraction
                ?: if (durationMs > 0) (currentTimeMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            cachedFraction = if (durationMs > 0) (cachedTimeMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            wavy = isPlaying && scrubFraction == null,
            enabled = durationMs > 0,
            positionLabel = "${formatTime(shownTime)} of ${formatTime(durationMs)}",
            onScrub = onScrub,
            onScrubEnd = onScrubEnd
        )
        Text(
            if (showRemaining) "-${formatTime(durationMs - shownTime)}" else formatTime(durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showRemaining = !showRemaining }
        )
    }
}

/** A flat line that ripples behind the playhead while video plays, and shows what is buffered. */
@Composable
private fun WavySeekbar(
    modifier: Modifier,
    fraction: Float,
    cachedFraction: Float,
    wavy: Boolean,
    enabled: Boolean,
    positionLabel: String,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val waveHeight by animateFloatAsState(if (wavy) with(density) { 2.5.dp.toPx() } else 0f, label = "waveHeight")
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val latestOnScrub by rememberUpdatedState(onScrub)
    val latestOnScrubEnd by rememberUpdatedState(onScrubEnd)

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = "Video position"
                stateDescription = positionLabel
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        latestOnScrub(target.coerceIn(0f, 1f))
                        latestOnScrubEnd()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    latestOnScrub((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                            latestOnScrub((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                    latestOnScrubEnd()
                }
            }
    ) {
        val centerY = size.height / 2
        val stroke = 5.dp.toPx()
        val playedX = size.width * fraction
        val cachedX = size.width * cachedFraction.coerceAtLeast(fraction)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(playedX, centerY), Offset(size.width, centerY), stroke, StrokeCap.Round)
        if (cachedX > playedX) {
            drawLine(Color.White.copy(alpha = 0.55f), Offset(playedX, centerY), Offset(cachedX, centerY), stroke, StrokeCap.Round)
        }
        val wavelength = 26.dp.toPx()
        val path = Path().apply {
            moveTo(0f, centerY)
            var x = 0f
            while (x < playedX) {
                lineTo(x, centerY + waveHeight * sin(x / wavelength * 2 * PI.toFloat() - phase))
                x += 3f
            }
            lineTo(playedX, centerY + waveHeight * sin(playedX / wavelength * 2 * PI.toFloat() - phase))
        }
        drawPath(path, primary, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val thumbWidth = 6.dp.toPx()
        val thumbHeight = 24.dp.toPx()
        drawRoundRect(
            primary,
            topLeft = Offset(playedX - thumbWidth / 2, centerY - thumbHeight / 2),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2)
        )
    }
}

/** The tall slider beside the screen edge while swiping brightness or volume. */
@Composable
fun LevelSlider(icon: ImageVector, percent: Int, modifier: Modifier = Modifier) {
    val filled by animateFloatAsState((percent / 100f).coerceIn(0f, 1f), label = "level")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DockColor)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$percent", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .height(120.dp)
                .width(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(filled)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Fills one side of the screen after a double tap, like a thumb-shaped ripple. */
@Composable
fun DoubleTapSeekOval(forward: Boolean, seconds: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.35f)
            .clip(
                if (forward) {
                    RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                } else {
                    RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                }
            )
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                repeat(3) {
                    Icon(
                        if (forward) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                "${if (forward) "+" else "-"}${seconds}s",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/** Small top-centre message for hold-to-speed, swipe seeking and pinch zoom. */
@Composable
fun GestureMessage(text: String, modifier: Modifier = Modifier, showFastForward: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DockColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (showFastForward) {
            Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Replaces all controls while locked; dragging to the end is deliberate enough to survive a pocket. */
@Composable
fun SlideToUnlock(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val knobSize = 52.dp
    val trackWidth = 230.dp
    val density = LocalDensity.current
    val maxOffset = with(density) { (trackWidth - knobSize - 8.dp).toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(knobSize + 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DockColor)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "Slide to unlock",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Center).padding(start = 36.dp)
        )
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.roundToInt(), 0) }
                .size(knobSize)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value >= maxOffset * 0.9f) onUnlock() else offset.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + amount).coerceIn(0f, maxOffset)) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = "Unlock", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SheetFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .wrapContentHeight(Alignment.Bottom)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SheetColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SheetRow(
    text: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SheetChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.65f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun TimingRow(label: String, delayMs: Long, onChange: (Long) -> Unit) {
    SectionLabel("$label: ${String.format(java.util.Locale.US, "%+.2fs", delayMs / 1000f)}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetChip("Earlier", false, Modifier.weight(1f)) { onChange(delayMs - TIMING_STEP_MS) }
        SheetChip("Later", false, Modifier.weight(1f)) { onChange(delayMs + TIMING_STEP_MS) }
        SheetChip("Reset", delayMs != 0L, Modifier.weight(1f)) { onChange(0L) }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x"
    else "${String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0')}x"

/** Bottom sheets for tracks and settings, drawn inside the player so full screen is never interrupted. */
@Composable
fun PlayerSheets(
    sheet: PlayerSheet,
    audioTracks: List<PlayerTrack>,
    selectedAudio: Int,
    onSelectAudio: (Int) -> Unit,
    audioDelayMs: Long,
    onAudioDelayChange: (Long) -> Unit,
    subtitleTracks: List<PlayerTrack>,
    selectedSubtitle: Int,
    onSelectSubtitle: (Int) -> Unit,
    onAddSubtitleFile: () -> Unit,
    subtitleDelayMs: Long,
    onSubtitleDelayChange: (Long) -> Unit,
    subtitleSizePercent: Int,
    onSubtitleSizeChange: (Int) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    fitMode: String,
    onFitModeChange: (String) -> Unit,
    hardwareDecoding: Boolean,
    onHardwareDecodingChange: (Boolean) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit
) {
    AnimatedVisibility(
        visible = sheet != PlayerSheet.None,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        // Keeps the last sheet on screen while it animates away.
        var shown by remember { mutableStateOf(sheet) }
        if (sheet != PlayerSheet.None) shown = sheet
        val dismiss = { onOpenSheet(PlayerSheet.None) }
        when (shown) {
            PlayerSheet.Audio -> SheetFrame("Audio", dismiss) {
                audioTracks.forEach { track ->
                    SheetRow(track.name, selected = track.id == selectedAudio) { onSelectAudio(track.id) }
                }
                TimingRow("Audio timing", audioDelayMs, onAudioDelayChange)
            }
            PlayerSheet.Subtitles -> SheetFrame("Subtitles", dismiss) {
                SheetRow("Off", selected = selectedSubtitle == -1) { onSelectSubtitle(-1) }
                subtitleTracks.forEach { track ->
                    SheetRow(track.name, selected = track.id == selectedSubtitle) { onSelectSubtitle(track.id) }
                }
                SheetRow("Add subtitle file", icon = Icons.Outlined.FileOpen, onClick = onAddSubtitleFile)
                TimingRow("Subtitle timing", subtitleDelayMs, onSubtitleDelayChange)
                SectionLabel("Subtitle size")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUBTITLE_SIZES.forEach { (percent, label) ->
                        SheetChip(label, percent == subtitleSizePercent, Modifier.weight(1f)) {
                            onSubtitleSizeChange(percent)
                        }
                    }
                }
            }
            PlayerSheet.Speed -> SheetFrame("Speed: ${speedLabel(speed)}", dismiss) {
                Slider(
                    value = speed,
                    onValueChange = { onSpeedChange((it * 20).roundToInt() / 20f) },
                    valueRange = 0.25f..3f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SPEEDS.forEach { preset ->
                        SheetChip(speedLabel(preset), preset == speed, Modifier.weight(1f)) { onSpeedChange(preset) }
                    }
                }
            }
            PlayerSheet.Fit -> SheetFrame("Screen fit", dismiss) {
                FIT_MODES.forEach { mode ->
                    SheetRow(mode, selected = mode == fitMode) { onFitModeChange(mode) }
                }
            }
            PlayerSheet.More -> SheetFrame("More options", dismiss) {
                SheetRow("Speed", icon = Icons.Outlined.Speed, trailing = speedLabel(speed)) { onOpenSheet(PlayerSheet.Speed) }
                SheetRow("Screen fit", icon = Icons.Outlined.AspectRatio, trailing = fitMode) { onOpenSheet(PlayerSheet.Fit) }
                SheetRow("Audio", icon = Icons.Outlined.Audiotrack) { onOpenSheet(PlayerSheet.Audio) }
                SheetRow("Subtitles", icon = Icons.Outlined.Subtitles) { onOpenSheet(PlayerSheet.Subtitles) }
                SheetRow(
                    "Decoder",
                    icon = Icons.Outlined.Memory,
                    trailing = if (hardwareDecoding) "Hardware" else "Software"
                ) { onHardwareDecodingChange(!hardwareDecoding) }
            }
            PlayerSheet.None -> Unit
        }
    }
}
