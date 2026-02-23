package com.tazztone.losslesscut.domain.usecase

import android.net.Uri
import com.tazztone.losslesscut.data.MediaClip
import com.tazztone.losslesscut.data.VideoEditingRepository
import com.tazztone.losslesscut.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SessionUseCase @Inject constructor(
    private val repository: VideoEditingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun saveSession(clips: List<MediaClip>) = withContext(ioDispatcher) {
        repository.saveSession(clips)
    }

    suspend fun restoreSession(uri: Uri): List<MediaClip>? = withContext(ioDispatcher) {
        repository.restoreSession(uri)
    }

    suspend fun hasSavedSession(uri: Uri): Boolean = withContext(ioDispatcher) {
        repository.hasSavedSession(uri)
    }
}
