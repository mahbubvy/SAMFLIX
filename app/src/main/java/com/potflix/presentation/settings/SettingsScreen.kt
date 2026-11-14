package com.potflix.presentation.settings

import com.potflix.presentation.theme.*

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.BuildConfig
import com.potflix.R
import com.potflix.data.local.preferences.ServerConfig
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.theme.SamAccent
import com.potflix.util.LanguageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val cacheSize by viewModel.cacheSize.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showClearCacheDialog by remember { mutableStateOf(false) }
    val activeServer by viewModel.activeServer.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }
    var showSyncCodeDialog by remember { mutableStateOf(false) }
    var showAudioLangDialog by remember { mutableStateOf(false) }
    var showSubtitleLangDialog by remember { mutableStateOf(false) }
    var showTmdbDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    Scaffold(
        topBar = {
            com.potflix.presentation.common.SamTopBar(
                title = "Settings",
                onBack = { navController.popBackStack() }
            )
        },
        containerColor = SamBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── APP BRANDING HERO BANNER ───
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SamBackground),
                    border = BorderStroke(1.dp, SamSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        SamAccent.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                com.potflix.presentation.common.SamflixWordmark()
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SamTextMuted
                                )
                            }

                            // Status Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SamSuccess.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SamSuccess)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ready",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SamSuccess
                                )
                            }
                        }
                    }
                }
            }

            // ─── PLAYBACK & STREAMING GROUP ───
            item {
                val currentAudioLang by viewModel.preferredAudioLanguage.collectAsState()
                val currentSubtitleLang by viewModel.preferredSubtitleLanguage.collectAsState()

                SettingsCardGroup(title = "Streaming & Playback") {
                    ModernSettingsItem(
                        icon = Icons.Default.Dns,
                        iconTint = SamAccent, // Purple
                        title = "Content Server",
                        subtitle = activeServer.name,
                        trailingValue = activeServer.name,
                        onClick = { showServerDialog = true },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Audiotrack,
                        iconTint = SamAccent, // Blue
                        title = "Preferred Audio",
                        subtitle = LanguageUtils.getAudioLanguageDisplayName(currentAudioLang),
                        trailingValue = LanguageUtils.getAudioLanguageDisplayName(currentAudioLang),
                        onClick = { showAudioLangDialog = true },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Subtitles,
                        iconTint = SamAccent, // Cyan
                        title = "Preferred Subtitles",
                        subtitle = LanguageUtils.getSubtitleLanguageDisplayName(currentSubtitleLang),
                        trailingValue = LanguageUtils.getSubtitleLanguageDisplayName(currentSubtitleLang),
                        onClick = { showSubtitleLangDialog = true },
                        showDivider = false
                    )
                }
            }

            // ─── STORAGE & DATABASE GROUP ───
            item {
                val isSyncing by viewModel.isSyncing.collectAsState()
                val syncProgress by viewModel.syncProgress.collectAsState()
                val lastSyncTime by viewModel.lastSyncTime.collectAsState()

                SettingsCardGroup(title = "Storage & Database") {
                    ModernSettingsItem(
                        icon = Icons.Default.CleaningServices,
                        iconTint = SamWarning, // Orange
                        title = "Clear Cache",
                        subtitle = "Temporary streaming & poster cache ($cacheSize)",
                        trailingValue = cacheSize,
                        isLoading = isClearingCache,
                        onClick = { showClearCacheDialog = true },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Sync,
                        iconTint = SamSuccess, // Emerald
                        title = if (isSyncing) "Syncing Database..." else "Sync Database",
                        subtitle = if (isSyncing) syncProgress else "Last: $lastSyncTime",
                        isLoading = isSyncing,
                        onClick = { if (!isSyncing) viewModel.syncDatabase() },
                        showDivider = false
                    )
                }
            }

            // ─── PREFERENCES & METADATA GROUP ───
            item {
                val dailyNotifEnabled by viewModel.dailyNotificationEnabled.collectAsState()
                val customTmdbKey by viewModel.customTmdbApiKey.collectAsState()
                val tmdbStatus = if (customTmdbKey.isNotBlank()) "Personal Key" else "Default Key"

                SettingsCardGroup(title = "Preferences & Metadata") {
                    ModernSettingsSwitchItem(
                        icon = Icons.Default.NotificationsActive,
                        iconTint = SamAccent, // Pink
                        title = "Daily Recommendations",
                        subtitle = "Receive daily movie & series suggestions",
                        checked = dailyNotifEnabled,
                        onCheckedChange = { viewModel.setDailyNotificationEnabled(it) },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Key,
                        iconTint = SamWarning, // Amber
                        title = "TMDB API Key",
                        subtitle = "Personal key avoids shared rate limits",
                        trailingValue = tmdbStatus,
                        onClick = { showTmdbDialog = true },
                        showDivider = false
                    )
                }
            }

            // ─── SYNC ───
            item {
                val sync by viewModel.librarySync.status.collectAsState()
                SettingsCardGroup(title = "Sync between devices") {
                    ModernSettingsSwitchItem(
                        icon = Icons.Default.Sync,
                        iconTint = SamAccent,
                        title = "Sync on Wi-Fi",
                        subtitle = "Share watchlist and history with your other devices on the same Wi-Fi. Nothing goes to the cloud.",
                        checked = sync.enabled,
                        onCheckedChange = { if (it) viewModel.librarySync.enable() else viewModel.librarySync.disable() },
                        showDivider = sync.enabled
                    )
                    if (sync.enabled) {
                        ModernSettingsItem(
                            icon = Icons.Default.Key,
                            iconTint = SamAccent,
                            title = "Sync code",
                            subtitle = "Type this code on your other devices to link them",
                            trailingValue = sync.code?.let { com.potflix.sync.SyncCrypto.format(it) },
                            onClick = { showSyncCodeDialog = true },
                            showDivider = true
                        )
                        val lastSync = sync.lastSyncAt?.let {
                            "Last synced with ${sync.lastSyncWith} " +
                                android.text.format.DateUtils.getRelativeTimeSpanString(it).toString().lowercase()
                        }
                        ModernSettingsItem(
                            icon = Icons.Default.Devices,
                            iconTint = if (sync.problem != null) SamError else SamAccent,
                            title = if (sync.nearby.isEmpty()) "No devices found yet" else sync.nearby.joinToString(", "),
                            subtitle = sync.problem ?: lastSync
                                ?: "Open SAMFLIX on another device on this Wi-Fi with the same code",
                            trailingBadge = if (sync.nearby.isNotEmpty()) "Sync now" else null,
                            onClick = { viewModel.librarySync.syncNow() },
                            showDivider = false
                        )
                    }
                }
            }

            // ─── COMMUNITY & SUPPORT GROUP ───
            item {
                SettingsCardGroup(title = "Community & Support") {
                    // Telegram Community Item
                    ModernSettingsItem(
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = SamAccent, // Telegram Blue
                        title = "Telegram Community",
                        subtitle = "Requests, feedback & announcements • @PotFlixx",
                        trailingBadge = "Join ↗",
                        badgeColor = SamAccent,
                        onClick = {
                            uriHandler.openUri("https://t.me/PotFlixx")
                        },
                        showDivider = true
                    )

                    // Developer Info
                    ModernSettingsItem(
                        icon = Icons.Default.Code,
                        iconTint = SamAccent, // Violet
                        title = "Developer Portfolio",
                        subtitle = "reduan.vercel.app ↗",
                        onClick = {
                            uriHandler.openUri("https://reduan.vercel.app")
                        },
                        showDivider = true
                    )

                    // Version, and a manual update check
                    ModernSettingsItem(
                        icon = Icons.Default.SystemUpdate,
                        iconTint = SamAccent,
                        title = "Check for updates",
                        subtitle = "Looks for a newer version on GitHub and offers to install it",
                        trailingValue = "v${BuildConfig.VERSION_NAME}",
                        onClick = { viewModel.checkForUpdates() },
                        showDivider = false
                    )
                }
            }
        }
    }

    // ─── DIALOGS ───

    if (showSyncCodeDialog) {
        val sync by viewModel.librarySync.status.collectAsState()
        var typed by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        var confirmNewCode by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSyncCodeDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = { Text("Sync code", fontWeight = FontWeight.Bold, color = SamText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Devices with the same code share their watchlist and history. This device's code:",
                        color = SamTextMuted, style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        sync.code?.let { com.potflix.sync.SyncCrypto.format(it) } ?: "",
                        color = SamText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    Text("To link to another device, type its code:", color = SamTextMuted, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.uppercase(); invalid = false },
                        singleLine = true,
                        placeholder = { Text("XXXXX-XXXXX") },
                        isError = invalid,
                        supportingText = if (invalid) { { Text("That isn't a valid code. Codes have 10 letters and digits.") } } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { confirmNewCode = true }) {
                        Text("Make a new code (unlinks other devices)", color = SamTextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.isNotBlank(),
                    onClick = {
                        if (viewModel.librarySync.join(typed)) {
                            showSyncCodeDialog = false
                            Toast.makeText(context, "Linked. Syncing with devices that use this code.", Toast.LENGTH_SHORT).show()
                        } else invalid = true
                    }
                ) { Text("Link", color = SamAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showSyncCodeDialog = false }) { Text("Close", color = SamTextMuted) }
            }
        )
        if (confirmNewCode) {
            AlertDialog(
                onDismissRequest = { confirmNewCode = false },
                containerColor = SamSurface,
                title = { Text("Make a new code?", color = SamText) },
                text = { Text("Other devices stop syncing with this one until you type the new code on them.", color = SamTextMuted) },
                confirmButton = {
                    TextButton(onClick = { viewModel.librarySync.newCode(); confirmNewCode = false }) {
                        Text("New code", color = SamAccent)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmNewCode = false }) { Text("Cancel", color = SamTextMuted) } }
            )
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = {
                Text("Select Content Server", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ServerConfig.BUILT_IN_SERVERS.forEach { server ->
                        val isSelected = activeServer.id == server.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SamAccent.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.setActiveServer(server)
                                    showServerDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setActiveServer(server)
                                    showServerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SamAccent,
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(server.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                Text(server.baseUrl, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }

    if (showAudioLangDialog) {
        val currentAudio by viewModel.preferredAudioLanguage.collectAsState()
        AlertDialog(
            onDismissRequest = { showAudioLangDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = { Text("Preferred Audio Language", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LanguageUtils.AUDIO_LANGUAGES.forEach { lang ->
                        val isSelected = currentAudio == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SamAccent.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.setPreferredAudioLanguage(lang.code)
                                    showAudioLangDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPreferredAudioLanguage(lang.code)
                                    showAudioLangDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SamAccent,
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioLangDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }

    if (showSubtitleLangDialog) {
        val currentSub by viewModel.preferredSubtitleLanguage.collectAsState()
        AlertDialog(
            onDismissRequest = { showSubtitleLangDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = { Text("Preferred Subtitle Language", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LanguageUtils.SUBTITLE_LANGUAGES.forEach { lang ->
                        val isSelected = currentSub == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SamAccent.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.setPreferredSubtitleLanguage(lang.code)
                                    showSubtitleLangDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPreferredSubtitleLanguage(lang.code)
                                    showSubtitleLangDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SamAccent,
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleLangDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }

    val showRestartDialog by viewModel.showRestartDialog.collectAsState()
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestartDialog() },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = { Text("Restart Required", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("You have switched the content server to ${activeServer.name}. Please restart the app for changes to take full effect.", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                val activity = LocalContext.current as? android.app.Activity
                Button(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                        activity?.finish()
                        Runtime.getRuntime().exit(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restart Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestartDialog() }) {
                    Text("Later", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
    
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = { Text("Clear App Cache", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Are you sure you want to clear temporary cached images and streams? ($cacheSize will be freed).", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
    
    if (showTmdbDialog) {
        val currentCustomKey by viewModel.customTmdbApiKey.collectAsState()
        var inputKey by remember(currentCustomKey) { mutableStateOf(currentCustomKey) }

        AlertDialog(
            onDismissRequest = { showTmdbDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = SamSurface,
            title = { Text("TMDB API Key", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "SAMFLIX uses The Movie Database (TMDB) for posters, ratings, and synopsis. Enter your personal TMDB API key to avoid shared rate limits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = "Get a free API key at themoviedb.org ↗",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SamAccent,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .clickable {
                                uriHandler.openUri("https://www.themoviedb.org/settings/api")
                            }
                            .padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = { Text("TMDB API Key") },
                        placeholder = { Text("v3 API key or v4 Bearer Token") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedLabelColor = SamAccent,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            if (inputKey.isNotEmpty()) {
                                IconButton(onClick = { inputKey = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    )

                    if (currentCustomKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.setCustomTmdbApiKey("")
                                showTmdbDialog = false
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Reset to General Default Key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomTmdbApiKey(inputKey)
                        showTmdbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Key", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTmdbDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

// ─── HELPER COMPOSABLES ───

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White.copy(alpha = 0.5f),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCardGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        SettingsSectionHeader(title)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SamBackground),
            border = BorderStroke(1.dp, SamSurface)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ModernSettingsItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailingValue: String? = null,
    trailingBadge: String? = null,
    badgeColor: Color = SamAccent,
    isLoading: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLoading, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Accessory
            if (isLoading) {
                CircularProgressIndicator(
                    color = SamAccent,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (trailingBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = trailingBadge,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (trailingValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trailingValue,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = SamSurface,
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 68.dp)
            )
        }
    }
}

@Composable
private fun ModernSettingsSwitchItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SamAccent,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = SamSurfaceRaised
                )
            )
        }

        if (showDivider) {
            HorizontalDivider(
                color = SamSurface,
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 68.dp)
            )
        }
    }
}
