package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SessionRestoreResult
import com.tazztone.losslesscut.domain.model.SessionSummary
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

public class SessionUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    public suspend fun saveSession(sessionId: String, clips: List<MediaClip>): Result<Unit> = withContext(ioDispatcher) {
        repository.saveSession(sessionId, clips)
    }

    public suspend fun restoreSession(sessionId: String): SessionRestoreResult? = withContext(ioDispatcher) {
        repository.restoreSession(sessionId)
    }

    public suspend fun hasSavedSession(sessionId: String): Boolean = withContext(ioDispatcher) {
        repository.hasSavedSession(sessionId)
    }

    public suspend fun listSavedSessions(): List<SessionSummary> = withContext(ioDispatcher) {
        repository.listSavedSessions()
    }

    public suspend fun deleteSession(sessionId: String): Unit = withContext(ioDispatcher) {
        repository.deleteSession(sessionId)
    }
}
