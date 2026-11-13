package com.potflix.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Search : Screen("search") {
        const val routeWithArgs = "search?query={query}"
        fun createRoute(query: String = ""): String {
            return if (query.isNotBlank()) "search?query=${android.net.Uri.encode(query)}" else "search"
        }
    }
    object Browse : Screen("browse")
    object BrowseFolder : Screen("browse_folder/{url}/{title}?series={series}") {
        fun createRoute(url: String, title: String, isSeries: Boolean): String {
            return "browse_folder/${android.net.Uri.encode(url)}/${android.net.Uri.encode(title)}?series=$isSeries"
        }
    }
    object Library : Screen("library")
    object Settings : Screen("settings")
    object CategoryDetail : Screen("category/{categoryId}/{categoryName}?type={type}") {
        fun createRoute(categoryId: String, categoryName: String, type: String = "category"): String {
            val encodedId = android.net.Uri.encode(categoryId)
            val encodedName = android.net.Uri.encode(categoryName)
            return "category/$encodedId/$encodedName?type=$type"
        }
    }
    object Detail : Screen("detail/{movieJson}") {
        fun createRoute(movie: com.potflix.domain.model.Movie): String {
            val json = com.google.gson.Gson().toJson(movie)
            val encoded = android.util.Base64.encodeToString(json.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            return "detail/$encoded"
        }
    }
    object Player : Screen("player/{movieUrl}/{streamUrl}/{title}/{playbackPosition}") {
        fun createRoute(movieUrl: String, streamUrl: String, title: String, playbackPosition: Long = 0L): String {
            val encodedMovie = android.net.Uri.encode(movieUrl)
            val encodedStream = android.net.Uri.encode(streamUrl)
            val encodedTitle = android.net.Uri.encode(title)
            return "player/$encodedMovie/$encodedStream/$encodedTitle/$playbackPosition"
        }
    }
}
