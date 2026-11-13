package com.potflix.data.remote

import com.potflix.data.local.entity.LocalMovieEntity
import com.potflix.domain.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This build has no accounts or cloud sync: watch history and the watchlist stay on
 * the device. The class keeps its original shape so existing callers do not change.
 */
@Singleton
class FirebaseSyncManager @Inject constructor() {
    val isUserLoggedIn: Boolean = false
    val currentUserEmail: Flow<String?> = flowOf(null)

    suspend fun initAuth() = Unit
    suspend fun signUp(email: String, password: String): Result<Unit> = accountsDisabled()
    suspend fun login(email: String, password: String): Result<Unit> = accountsDisabled()
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = accountsDisabled()
    suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
    suspend fun logout() = Unit

    fun syncWatchHistory(history: List<Movie>) = Unit
    fun syncWatchlist(watchlist: List<LocalMovieEntity>) = Unit

    private fun accountsDisabled(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Accounts are not used in this build."))

    fun suggestTmdbCorrection(
        originalTitle: String,
        url: String,
        correctedTmdbId: Long,
        correctedTitle: String,
        type: String
    ) = Unit
}
