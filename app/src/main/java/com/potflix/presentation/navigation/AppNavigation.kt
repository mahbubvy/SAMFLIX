package com.potflix.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.potflix.presentation.theme.SamAccent
import com.potflix.presentation.theme.SamSurface
import com.potflix.presentation.theme.SamSurfaceRaised
import com.potflix.presentation.theme.SamText
import com.potflix.presentation.theme.SamTextMuted
import com.potflix.presentation.theme.Space

/** The four top-level places in the app. */
enum class AppTab(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    /** Other routes that belong to this tab, so it stays highlighted inside it. */
    val childRoutes: Set<String> = emptySet()
) {
    Home("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home, setOf(Screen.CategoryDetail.route)),
    Browse("Browse", Screen.Browse.route, Icons.Filled.Folder, Icons.Outlined.Folder, setOf(Screen.BrowseFolder.route)),
    Search("Search", Screen.Search.route, Icons.Filled.Search, Icons.Outlined.Search, setOf(Screen.Search.routeWithArgs)),
    Library("Library", Screen.Library.route, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary);

    fun owns(route: String?) = route == this.route || route in childRoutes
}

/** Screens that cover the whole window, without the tab bar or rail. */
private val fullScreenRoutes = setOf(
    Screen.Onboarding.route,
    Screen.Detail.route,
    Screen.Player.route,
    Screen.Settings.route
)

@Composable
private fun currentRoute(navController: NavController): String? {
    val entry by navController.currentBackStackEntryAsState()
    return entry?.destination?.route
}

/** Opens a tab, keeping each tab's own history so coming back lands where you left it. */
private fun NavController.openTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** True when the current screen shows the tab bar or rail. */
@Composable
fun showsAppNavigation(navController: NavController): Boolean {
    val route = currentRoute(navController)
    return route != null && route !in fullScreenRoutes
}

/** Bottom tab bar for phones. Labels are always shown. */
@Composable
fun AppBottomBar(navController: NavController) {
    if (!showsAppNavigation(navController)) return
    val route = currentRoute(navController)
    NavigationBar(containerColor = SamSurface, contentColor = SamText) {
        AppTab.entries.forEach { tab ->
            val selected = tab.owns(route)
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected || route != tab.route) navController.openTab(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SamAccent,
                    selectedTextColor = SamAccent,
                    indicatorColor = SamSurfaceRaised,
                    unselectedIconColor = SamTextMuted,
                    unselectedTextColor = SamTextMuted
                )
            )
        }
    }
}

/** Side rail for tablets and other wide windows. */
@Composable
fun AppNavRail(navController: NavController, modifier: Modifier = Modifier) {
    if (!showsAppNavigation(navController)) return
    val route = currentRoute(navController)
    NavigationRail(
        modifier = modifier,
        containerColor = SamSurface,
        contentColor = SamText,
        windowInsets = WindowInsets.systemBars
    ) {
        Spacer(Modifier.height(Space.l))
        Column {
            AppTab.entries.forEach { tab ->
                val selected = tab.owns(route)
                NavigationRailItem(
                    selected = selected,
                    onClick = { if (!selected || route != tab.route) navController.openTab(tab) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = SamAccent,
                        selectedTextColor = SamAccent,
                        indicatorColor = SamSurfaceRaised,
                        unselectedIconColor = SamTextMuted,
                        unselectedTextColor = SamTextMuted
                    )
                )
                Spacer(Modifier.height(Space.s))
            }
        }
    }
}
