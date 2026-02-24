package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

public class SessionUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    public suspend fun saveSession(clips: List<MediaClip>): Unit = withContext(ioDispatcher) {
        repository.saveSession(clips)
    }

    public suspend fun restoreSession(uri: String): List<MediaClip>? = withContext(ioDispatcher) {
        repository.restoreSession(uri)
    }

    public suspend fun hasSavedSession(uri: String): Boolean = withContext(ioDispatcher) {
        repository.hasSavedSession(uri)
    }
}
