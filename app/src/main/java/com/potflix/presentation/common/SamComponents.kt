package com.potflix.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.potflix.presentation.theme.MinTouchTarget
import com.potflix.presentation.theme.SamAccent
import com.potflix.presentation.theme.SamOutline
import com.potflix.presentation.theme.SamShapes
import com.potflix.presentation.theme.SamSurface
import com.potflix.presentation.theme.SamText
import com.potflix.presentation.theme.SamTextMuted
import com.potflix.presentation.theme.Space
import com.potflix.presentation.theme.screenEdgePadding

/** The app's name as a wordmark: SAM in white, FLIX in the accent blue. */
@Composable
fun SamflixWordmark(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = SamText)) { append("SAM") }
            withStyle(SpanStyle(color = SamAccent)) { append("FLIX") }
        },
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        modifier = modifier.semantics { heading() }
    )
}

/** A row of single-choice filter chips, such as All / Movies / Series. */
@Composable
fun SamChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                shape = SamShapes.pill,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SamSurface,
                    labelColor = SamText,
                    selectedContainerColor = SamAccent,
                    selectedLabelColor = SamText
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = SamOutline,
                    selectedBorderColor = SamAccent
                ),
                modifier = Modifier.heightIn(min = MinTouchTarget)
            )
        }
    }
}

/** A plain top bar: optional back arrow, a title, and actions on the right. */
@Composable
fun SamTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(start = if (onBack == null) screenEdgePadding() else Space.xs, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SamText)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = SamText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        actions()
    }
}

/** The gear that opens Settings, used in the Home and Library top bars. */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = SamText)
    }
}

/** A muted one-line note, for things like "Syncing library…". */
@Composable
fun SamCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SamTextMuted, modifier = modifier)
}

