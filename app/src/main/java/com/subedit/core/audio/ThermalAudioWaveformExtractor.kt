package com.subedit.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.subedit.core.domain.SubTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class WaveformData(
    val peaks: FloatArray,
    val sampleRateHz: Int = DEFAULT_BUCKETS_PER_SEC,
    val durationUs: Long,
    val sourceLabel: String = "DEMO",
) {
    fun peakAt(timeUs: Long): Float {
        if (peaks.isEmpty() || timeUs < 0L || timeUs > durationUs) return 0.04f
        val index = ((timeUs * sampleRateHz) / SubTime.US_PER_SEC).toInt()
        return if (index in peaks.indices) peaks[index] else 0.04f
    }

    companion object {
        const val DEFAULT_BUCKETS_PER_SEC: Int = 50
    }
}

object ThermalAudioWaveformExtractor {
    private const val TIMEOUT_US: Long = 10_000L
    private const val SAMPLE_STRIDE: Int = 4
    private const val YIELD_INTERVAL_BUFFERS: Int = 32
    private const val PARTIAL_EMIT_INTERVAL_BUCKETS: Int = 750

    private val backgroundExtractorDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val memoryCache = ConcurrentHashMap<String, WaveformData>()

    suspend fun extractFromUri(
        context: Context,
        mediaUri: Uri,
        bucketsPerSecond: Int = WaveformData.DEFAULT_BUCKETS_PER_SEC,
        onProgressPercent: ((Int) -> Unit)? = null,
        onPartialWaveform: ((WaveformData) -> Unit)? = null,
    ): Result<WaveformData> = withContext(backgroundExtractorDispatcher) {
        val cacheKey = "${mediaUri}_$bucketsPerSecond"
        memoryCache[cacheKey]?.let { cached ->
            onProgressPercent?.invoke(100)
            onPartialWaveform?.invoke(cached)
            return@withContext Result.success(cached)
        }

        yield()

        runCatching {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                if (mediaUri.scheme == "file") {
                    extractor.setDataSource(mediaUri.path ?: mediaUri.toString())
                } else {
                    extractor.setDataSource(context, mediaUri, null)
                }
                val audioTrackIndex = selectAudioTrack(extractor)
                    ?: error("Tidak ditemukan trek audio pada file media.")

                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: error("MIME type audio tidak valid.")
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(SubTime.US_PER_SEC)
                } else {
                    60L * SubTime.US_PER_SEC
                }
                val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                } else {
                    1
                }

                val estimatedBuckets = (((durationUs / SubTime.US_PER_SEC) + 2L) * bucketsPerSecond)
                    .toInt()
                    .coerceIn(64, 360_000)
                val peaks = FloatArray(estimatedBuckets) { 0.05f }

                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }

                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var maxObservedPeak = 0.1f
                var processedBuffers = 0
                var lastEmittedBucket = 0
                var lastReportedPercent = -1

                while (!outputDone) {
                    coroutineContext.ensureActive()
                    processedBuffers++
                    if (processedBuffers % YIELD_INTERVAL_BUFFERS == 0) {
                        yield()
                    }

                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                    if (onProgressPercent != null && durationUs > 0L) {
                                        val pct = ((ptsUs * 100L) / durationUs).toInt().coerceIn(0, 100)
                                        if (pct >= lastReportedPercent + 2) {
                                            lastReportedPercent = pct
                                            onProgressPercent(pct)
                                        }
                                    }
                                    codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIndex >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            val shortBuffer = outBuffer.asShortBuffer()
                            val totalShorts = shortBuffer.remaining()
                            var peakAbs = 0
                            var s = 0
                            val step = (channelCount * SAMPLE_STRIDE).coerceAtLeast(1)
                            while (s < totalShorts) {
                                val sample = abs(shortBuffer.get(s).toInt())
                                if (sample > peakAbs) peakAbs = sample
                                s += step
                            }

                            val bucketIdx = ((bufferInfo.presentationTimeUs.coerceAtLeast(0L) * bucketsPerSecond) /
                                SubTime.US_PER_SEC).toInt()
                            if (bucketIdx in peaks.indices) {
                                val normalized = (peakAbs / 32768f).coerceIn(0.04f, 1f)
                                if (normalized > peaks[bucketIdx]) {
                                    peaks[bucketIdx] = normalized
                                }
                                if (normalized > maxObservedPeak) {
                                    maxObservedPeak = normalized
                                }
                                if (onPartialWaveform != null &&
                                    bucketIdx - lastEmittedBucket >= PARTIAL_EMIT_INTERVAL_BUCKETS
                                ) {
                                    lastEmittedBucket = bucketIdx
                                    onPartialWaveform(
                                        WaveformData(
                                            peaks = peaks.copyOf(),
                                            sampleRateHz = bucketsPerSecond,
                                            durationUs = durationUs,
                                            sourceLabel = "PCM 50Hz",
                                        )
                                    )
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }

                if (maxObservedPeak > 0.05f && maxObservedPeak < 0.95f) {
                    val scale = 1f / maxObservedPeak
                    for (i in peaks.indices) {
                        peaks[i] = (peaks[i] * scale).coerceIn(0.04f, 1f)
                    }
                }

                val data = WaveformData(
                    peaks = peaks,
                    sampleRateHz = bucketsPerSecond,
                    durationUs = durationUs,
                    sourceLabel = "PCM 50Hz",
                )
                onProgressPercent?.invoke(100)
                memoryCache[cacheKey] = data
                data
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor.release() }
            }
        }
    }

    fun generateDemoWaveform(
        durationUs: Long = 60L * SubTime.US_PER_SEC,
        bucketsPerSecond: Int = WaveformData.DEFAULT_BUCKETS_PER_SEC,
    ): WaveformData {
        val cacheKey = "DEMO_${durationUs}_$bucketsPerSecond"
        memoryCache[cacheKey]?.let { return it }

        val totalBuckets = ((durationUs * bucketsPerSecond) / SubTime.US_PER_SEC).toInt().coerceAtLeast(100)
        val peaks = FloatArray(totalBuckets)

        for (i in 0 until totalBuckets) {
            val t = i.toDouble() / bucketsPerSecond
            val rawAmp = sin(t * 21.0) * cos(t * 7.5) * sin(t * 2.3)
            val speechBurst = if (sin(t * 1.4) > -0.25) 0.85 else 0.22
            val value = maxOf(0.06, abs(rawAmp) * speechBurst + 0.05)
                .toFloat()
                .coerceIn(0.05f, 0.98f)
            peaks[i] = value
        }

        return WaveformData(
            peaks = peaks,
            sampleRateHz = bucketsPerSecond,
            durationUs = durationUs,
            sourceLabel = "DEMO",
        ).also { memoryCache[cacheKey] = it }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }
}
