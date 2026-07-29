package com.tazztone.losslesscut.data

import android.content.Context
import android.util.Log
import com.tazztone.losslesscut.domain.cache.IAnalysisCache
import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.HashUtils
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.model.WaveformResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class AnalysisCacheImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : IAnalysisCache {

    private val cacheDir: File by lazy {
        File(context.noBackupFilesDir, "analysis_cache").also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    private val lock = Any()

    @Volatile
    private var maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES

    @Volatile
    private var maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS

    init {
        evictLegacyWaveformCache()
    }

    private fun getClipIdentityHash(clip: MediaClip): String {
        val input = "${clip.uri}_${clip.durationMs}_${clip.width}x${clip.height}_" +
                "${clip.videoMime}_${clip.audioMime}_${clip.sampleRate}_${clip.channelCount}_" +
                "${clip.fps}_${clip.rotation}_${clip.isAudioOnly}"
        return HashUtils.sha256(input)
    }

    override fun getWaveform(clip: MediaClip): WaveformResult? = synchronized(lock) {
        val clipHash = getClipIdentityHash(clip)
        val file = File(cacheDir, "waveform_${clipHash}_v2.bin")
        if (!file.exists()) return null

        try {
            DataInputStream(FileInputStream(file)).use { input ->
                val version = input.readInt()
                if (version != WAVEFORM_PAYLOAD_VERSION) {
                    file.delete()
                    return null
                }
                val durationUs = input.readLong()
                val maxAmplitude = input.readFloat()
                val size = input.readInt()
                val expectedByteCount = size.toLong() * Float.SIZE_BYTES
                if (size <= 0 || size > MAX_WAVEFORM_SAMPLES ||
                    expectedByteCount > file.length() - WAVEFORM_HEADER_BYTES
                ) {
                    file.delete()
                    return null
                }
                val bytes = ByteArray(size * Float.SIZE_BYTES)
                input.readFully(bytes)
                val amplitudes = FloatArray(size)
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .asFloatBuffer()
                    .get(amplitudes)

                file.setLastModified(System.currentTimeMillis())
                return WaveformResult(amplitudes, maxAmplitude, durationUs)
            }
        } catch (e: Exception) {
            Log.e("AnalysisCacheImpl", "Corrupt waveform cache file, deleting: ${file.name}", e)
            file.delete()
            return null
        }
    }

    override fun saveWaveform(clip: MediaClip, waveform: WaveformResult): Unit = synchronized(lock) {
        if (waveform.rawAmplitudes.isEmpty() || waveform.rawAmplitudes.size > MAX_WAVEFORM_SAMPLES) {
            return
        }
        val clipHash = getClipIdentityHash(clip)
        val targetFile = File(cacheDir, "waveform_${clipHash}_v2.bin")
        val tmpFile = File(cacheDir, "waveform_${clipHash}_v2.bin.tmp")

        try {
            DataOutputStream(FileOutputStream(tmpFile)).use { out ->
                out.writeInt(WAVEFORM_PAYLOAD_VERSION)
                out.writeLong(waveform.durationUs)
                out.writeFloat(waveform.maxAmplitude)
                out.writeInt(waveform.rawAmplitudes.size)

                val byteBuffer = ByteBuffer.allocate(waveform.rawAmplitudes.size * Float.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                byteBuffer.asFloatBuffer().put(waveform.rawAmplitudes)
                out.write(byteBuffer.array())
            }
            replaceAtomically(tmpFile, targetFile)
            pruneInternal()
        } catch (e: Exception) {
            Log.e("AnalysisCacheImpl", "Failed to save waveform to cache", e)
            tmpFile.delete()
        }
    }

    override fun getFrameAnalysis(
        clip: MediaClip,
        strategy: VisualStrategy,
        sampleIntervalMs: Long
    ): List<FrameAnalysis>? = synchronized(lock) {
        val clipHash = getClipIdentityHash(clip)
        val file = File(cacheDir, "visual_${clipHash}_${strategy.name}_${sampleIntervalMs}_v1.bin")
        if (!file.exists()) return null

        try {
            DataInputStream(FileInputStream(file)).use { input ->
                val version = input.readInt()
                if (version != VISUAL_PAYLOAD_VERSION) {
                    file.delete()
                    return null
                }
                val count = input.readInt()
                if (count < 0 || count > MAX_FRAME_ANALYSIS_SAMPLES ||
                    count.toLong() * MIN_FRAME_ANALYSIS_BYTES > file.length() - VISUAL_HEADER_BYTES
                ) {
                    file.delete()
                    return null
                }

                val result = ArrayList<FrameAnalysis>(count)
                repeat(count) {
                    val timeMs = input.readLong()
                    val meanLuma = input.readDouble()
                    val blurVariance = input.readDouble()
                    val hasSceneDistance = input.readBoolean()
                    val sceneDistance = if (hasSceneDistance) input.readInt() else null
                    val hasFreezeDiff = input.readBoolean()
                    val freezeDiff = if (hasFreezeDiff) input.readDouble() else null

                    result.add(
                        FrameAnalysis(
                            timeMs = timeMs,
                            meanLuma = meanLuma,
                            blurVariance = blurVariance,
                            sceneDistance = sceneDistance,
                            freezeDiff = freezeDiff
                        )
                    )
                }

                file.setLastModified(System.currentTimeMillis())
                return result
            }
        } catch (e: Exception) {
            Log.e("AnalysisCacheImpl", "Corrupt visual frame analysis cache file, deleting: ${file.name}", e)
            file.delete()
            return null
        }
    }

    override fun saveFrameAnalysis(
        clip: MediaClip,
        strategy: VisualStrategy,
        sampleIntervalMs: Long,
        analysis: List<FrameAnalysis>
    ): Unit = synchronized(lock) {
        if (analysis.size > MAX_FRAME_ANALYSIS_SAMPLES) return
        val clipHash = getClipIdentityHash(clip)
        val targetFile = File(cacheDir, "visual_${clipHash}_${strategy.name}_${sampleIntervalMs}_v1.bin")
        val tmpFile = File(cacheDir, "visual_${clipHash}_${strategy.name}_${sampleIntervalMs}_v1.bin.tmp")

        try {
            DataOutputStream(FileOutputStream(tmpFile)).use { out ->
                out.writeInt(VISUAL_PAYLOAD_VERSION)
                out.writeInt(analysis.size)

                for (frame in analysis) {
                    out.writeLong(frame.timeMs)
                    out.writeDouble(frame.meanLuma)
                    out.writeDouble(frame.blurVariance)
                    val sd = frame.sceneDistance
                    if (sd != null) {
                        out.writeBoolean(true)
                        out.writeInt(sd)
                    } else {
                        out.writeBoolean(false)
                    }
                    val fd = frame.freezeDiff
                    if (fd != null) {
                        out.writeBoolean(true)
                        out.writeDouble(fd)
                    } else {
                        out.writeBoolean(false)
                    }
                }
            }
            replaceAtomically(tmpFile, targetFile)
            pruneInternal()
        } catch (e: Exception) {
            Log.e("AnalysisCacheImpl", "Failed to save visual frame analysis to cache", e)
            tmpFile.delete()
        }
    }

    override fun getCacheUsageBytes(): Long = synchronized(lock) {
        if (!cacheDir.exists()) return 0L
        return cacheDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sumOf { it.length() } ?: 0L
    }

    override fun clearCache(): Unit = synchronized(lock) {
        if (!cacheDir.exists()) return
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
    }

    override fun updateCachePolicy(maxSizeBytes: Long, maxAgeDays: Int): Unit = synchronized(lock) {
        this.maxSizeBytes = maxSizeBytes
        this.maxAgeDays = maxAgeDays
        pruneInternal()
    }

    private fun pruneInternal() {
        if (!cacheDir.exists()) return
        val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        val now = System.currentTimeMillis()
        val expiryThreshold = now - (maxAgeDays * MS_PER_DAY)

        // 1. Evict expired entries
        val remaining = mutableListOf<File>()
        for (file in files) {
            if (file.lastModified() < expiryThreshold) {
                file.delete()
            } else {
                remaining.add(file)
            }
        }

        // 2. Evict LRU entries if capacity exceeded
        var currentSize = remaining.sumOf { it.length() }
        if (currentSize > maxSizeBytes) {
            remaining.sortBy { it.lastModified() }
            val iterator = remaining.iterator()
            while (iterator.hasNext() && currentSize > maxSizeBytes) {
                val fileToDelete = iterator.next()
                val length = fileToDelete.length()
                if (fileToDelete.delete()) {
                    currentSize -= length
                }
            }
        }
    }

    private fun evictLegacyWaveformCache() {
        try {
            // Safely clean up old waveform files in cacheDir (including the mismatched .v3.bin files)
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("waveform_") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e("AnalysisCacheImpl", "Error cleaning legacy waveform cache", e)
        }
    }

    private fun replaceAtomically(tmpFile: File, targetFile: File) {
        try {
            Files.move(tmpFile.toPath(), targetFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmpFile.toPath(), targetFile.toPath(), REPLACE_EXISTING)
        }
    }

    companion object {
        private const val WAVEFORM_PAYLOAD_VERSION = 2
        private const val VISUAL_PAYLOAD_VERSION = 1
        private const val WAVEFORM_HEADER_BYTES = Int.SIZE_BYTES + Long.SIZE_BYTES + Float.SIZE_BYTES + Int.SIZE_BYTES
        private const val VISUAL_HEADER_BYTES = Int.SIZE_BYTES + Int.SIZE_BYTES
        private const val MIN_FRAME_ANALYSIS_BYTES = Long.SIZE_BYTES + Double.SIZE_BYTES + Double.SIZE_BYTES + 2L
        private const val DEFAULT_MAX_SIZE_BYTES = 250L * 1024L * 1024L // 250 MiB
        private const val DEFAULT_MAX_AGE_DAYS = 30
        private const val MS_PER_DAY = 86_400_000L
        private const val MAX_WAVEFORM_SAMPLES = 50_000_000
        private const val MAX_FRAME_ANALYSIS_SAMPLES = 1_000_000
    }
}
