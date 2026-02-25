package com.tazztone.losslesscut.di

import android.net.Uri
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import com.tazztone.losslesscut.utils.StorageUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [IMediaFinalizer] that delegates to [StorageUtils].
 * Located in :core:data to satisfy dependency on StorageUtils.
 */
@Singleton
class MediaFinalizerImpl @Inject constructor(
    private val storageUtils: StorageUtils
) : IMediaFinalizer {
    override fun finalizeVideo(uri: String) {
        storageUtils.finalizeVideo(Uri.parse(uri))
    }

    override fun finalizeAudio(uri: String) {
        storageUtils.finalizeAudio(Uri.parse(uri))
    }
}
