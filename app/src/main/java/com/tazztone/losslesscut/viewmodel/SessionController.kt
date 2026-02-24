package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Handles session save/restore logic.
 */
class SessionController(
    private val sessionUseCase: SessionUseCase,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun saveSession(clips: List<MediaClip>) = withContext(ioDispatcher) {
        sessionUseCase.saveSession(clips)
    }

    suspend fun restoreSession(uri: String): List<MediaClip>? = withContext(ioDispatcher) {
        sessionUseCase.restoreSession(uri)
    }

    suspend fun hasSavedSession(uri: String): Boolean = withContext(ioDispatcher) {
        sessionUseCase.hasSavedSession(uri)
    }
}
