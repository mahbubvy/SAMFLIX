package com.potflix.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.potflix.presentation.common.SamCaption
import com.potflix.presentation.common.SamChipRow
import com.potflix.presentation.common.SamflixWordmark
import com.potflix.presentation.common.SettingsAction
import com.potflix.presentation.theme.SamAccent
import com.potflix.presentation.theme.SamBackground
import com.potflix.presentation.theme.Space
import com.potflix.presentation.theme.screenEdgePadding

/** Which part of the catalogue the Home tab shows. */
enum class CatalogueFilter(val label: String) { All("All"), Movies("Movies"), Series("Series") }

/** The header shared by the Home tab's three views: wordmark, Settings, and the filter chips. */
@Composable
fun CatalogueHeader(
    filter: CatalogueFilter,
    onFilterChange: (CatalogueFilter) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    syncMessage: String? = null
) {
    val edge = screenEdgePadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(SamBackground.copy(alpha = 0.95f), SamBackground.copy(alpha = 0.7f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(bottom = Space.s)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = edge, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SamflixWordmark()
            SettingsAction(onClick = onOpenSettings)
        }
        SamChipRow(
            options = CatalogueFilter.entries.map { it.label },
            selectedIndex = filter.ordinal,
            onSelect = { onFilterChange(CatalogueFilter.entries[it]) },
            modifier = Modifier.padding(horizontal = edge)
        )
        if (syncMessage != null) {
            Row(
                modifier = Modifier.padding(start = edge, end = edge, top = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SamAccent, strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.s))
                SamCaption(syncMessage)
            }
        }
    }
}

/** How far content that must clear the header (like the pull-to-refresh spinner) sits from the top. */
val CatalogueHeaderHeight = 128.dp
