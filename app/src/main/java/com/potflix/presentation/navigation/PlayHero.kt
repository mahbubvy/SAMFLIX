package com.potflix.presentation.navigation

import androidx.navigation.NavController
import com.potflix.domain.model.Movie

private val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".webm")

/**
 * The hero banner's Play button: resumes or starts the video when it is known. Series without
 * a saved episode still open Detail, because someone has to choose where to start.
 */
fun NavController.playHero(movie: Movie, onNewPlayback: (Movie) -> Unit) {
    val position = movie.playbackPosition ?: 0L
    val resumeUrl = movie.lastPlayedStreamUrl?.takeIf { !movie.isWatched }
    when {
        resumeUrl != null -> navigate(Screen.Player.createRoute(movie.url, resumeUrl, movie.title, position))
        movie.type != "tv" && VIDEO_EXTENSIONS.any { movie.url.endsWith(it, ignoreCase = true) } -> {
            onNewPlayback(movie)
            navigate(Screen.Player.createRoute(movie.url, movie.url, movie.title, position))
        }
        else -> navigate(Screen.Detail.createRoute(movie))
    }
}
