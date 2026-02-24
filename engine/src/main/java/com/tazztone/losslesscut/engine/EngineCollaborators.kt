package com.tazztone.losslesscut.engine

import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import com.tazztone.losslesscut.engine.muxing.MergeValidator
import com.tazztone.losslesscut.engine.muxing.SampleTimeMapper
import com.tazztone.losslesscut.engine.muxing.TrackInspector
import javax.inject.Inject

/**
 * Groups engine collaborators to avoid long parameter lists in constructor.
 */
data class EngineCollaborators @Inject constructor(
    val dataSource: MediaDataSource,
    val inspector: TrackInspector,
    val timeMapper: SampleTimeMapper,
    val mergeValidator: MergeValidator
)
