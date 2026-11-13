package com.potflix.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spacing steps. Anything in between is a sign the layout needs another look. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner shapes: posters and chips 8, cards 12, sheets 20, pills fully round. */
object SamShapes {
    val poster = RoundedCornerShape(8.dp)
    val chip = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Minimum size of anything tappable. */
val MinTouchTarget = 48.dp

/** Windows this wide or wider get the tablet layout (side rail, wider margins). */
const val TABLET_MIN_WIDTH_DP = 600

/** True when the window is wide enough for the tablet layout. Uses the window, so split-screen counts. */
@Composable
@ReadOnlyComposable
fun isWideWindow(): Boolean = LocalConfiguration.current.screenWidthDp >= TABLET_MIN_WIDTH_DP

/** Side padding for screen content: 16 on phones, 24 on tablets. */
@Composable
@ReadOnlyComposable
fun screenEdgePadding(): Dp = if (isWideWindow()) Space.xl else Space.l
