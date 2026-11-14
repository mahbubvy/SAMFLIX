package com.potflix.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.potflix.presentation.theme.SamSurfaceRaised
import com.potflix.presentation.theme.SamText
import com.potflix.presentation.theme.SamTextMuted
import com.potflix.presentation.theme.Space

/**
 * Offers to install an update once one has downloaded. Updates are only looked for when the
 * user taps "Check for updates" in Settings; the app never checks on its own.
 */
@Composable
fun UpdatePrompt(updater: AppUpdater) {
    val state by updater.state.collectAsState()
    var dismissedVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }

    val ready = state as? UpdateState.Ready ?: return
    if (ready.version == dismissedVersion) return

    AlertDialog(
        onDismissRequest = { dismissedVersion = ready.version },
        containerColor = SamSurfaceRaised,
        title = { Text("Update ready", color = SamText) },
        text = {
            Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (waitingForPermission) {
                        "Once SAMFLIX is allowed to install apps, tap Install again."
                    } else {
                        "SAMFLIX ${ready.version} has downloaded. Installing keeps your history and settings."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SamText
                )
                if (ready.notes.isNotBlank()) {
                    Spacer(Modifier.height(Space.m))
                    Text(ready.notes, style = MaterialTheme.typography.bodySmall, color = SamTextMuted)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                waitingForPermission = updater.install(ready) == AppUpdater.InstallStep.NeedsPermission
            }) { Text("Install") }
        },
        dismissButton = {
            TextButton(onClick = { dismissedVersion = ready.version }) { Text("Later", color = SamText) }
        }
    )
}
