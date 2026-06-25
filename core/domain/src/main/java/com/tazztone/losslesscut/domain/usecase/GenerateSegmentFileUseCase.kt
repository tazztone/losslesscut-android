package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.util.SegmentFileExporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

class GenerateSegmentFileUseCase @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun execute(
        clips: List<MediaClip>,
        outputDir: File
    ): Result<File> = withContext(ioDispatcher) {
        try {
            if (clips.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No media clips available"))
            }

            val content = SegmentFileExporter.generateLlcContent(clips)
            val fileName = SegmentFileExporter.deriveLlcFileName(clips.first().fileName)
            val outputFile = File(outputDir, fileName)

            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return@withContext Result.failure(IOException("Cannot create output directory"))
            }

            outputFile.writeText(content)

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext Result.failure(IOException("Output file is empty or missing"))
            }

            Result.success(outputFile)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }
}