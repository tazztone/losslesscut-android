package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SessionUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun saveSession(clips: List<MediaClip>) = withContext(ioDispatcher) {
        repository.saveSession(clips)
    }

    suspend fun restoreSession(uri: String): List<MediaClip>? = withContext(ioDispatcher) {
        repository.restoreSession(uri)
    }

    suspend fun hasSavedSession(uri: String): Boolean = withContext(ioDispatcher) {
        repository.hasSavedSession(uri)
    }
}
