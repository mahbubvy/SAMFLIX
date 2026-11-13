package com.potflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.background
import com.potflix.presentation.navigation.AppBottomBar
import com.potflix.presentation.navigation.AppNavRail
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.theme.isWideWindow
import com.potflix.presentation.navigation.PotFlixNavGraph
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.potflix.worker.DailyNotificationScheduler

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var serverPreferences: com.potflix.data.local.preferences.ServerPreferences
    
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && serverPreferences.isDailyNotificationEnabled()) {
            DailyNotificationScheduler.scheduleNextDailyNotification(this)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request POST_NOTIFICATIONS on Android 13+ and ensure daily recommendations are scheduled
        checkNotificationPermissionAndSchedule()
        
        setContent {
            PotFlixTheme {
                val navController = rememberNavController()
                val startDest = if (serverPreferences.isOnboardingCompleted()) Screen.Home.route else Screen.Onboarding.route

                if (isWideWindow()) {
                    // Tablets and wide windows: tabs in a rail on the left.
                    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AppNavRail(navController = navController)
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PotFlixNavGraph(navController = navController, startDestination = startDest)
                        }
                    }
                } else {
                    // Phones: tabs in a bar along the bottom.
                    Scaffold(
                        bottomBar = { AppBottomBar(navController = navController) },
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0)
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding()),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PotFlixNavGraph(navController = navController, startDestination = startDest)
                        }
                    }
                }
            }
        }
    }

    private fun checkNotificationPermissionAndSchedule() {
        if (!serverPreferences.isDailyNotificationEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                DailyNotificationScheduler.scheduleNextDailyNotification(this)
            }
        } else {
            DailyNotificationScheduler.scheduleNextDailyNotification(this)
        }
    }
}
