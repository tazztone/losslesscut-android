package com.tazztone.losslesscut.domain.engine

/**
 * Interface to handle post-processing of media files after extraction/muxing.
 * This separates the engine from the data layer's storage management.
 */
public interface IMediaFinalizer {
    /**
     * Finalizes a video file (e.g., adding to MediaStore, updating metadata).
     */
    public fun finalizeVideo(uri: String)

    /**
     * Finalizes an audio file.
     */
    public fun finalizeAudio(uri: String)
}
