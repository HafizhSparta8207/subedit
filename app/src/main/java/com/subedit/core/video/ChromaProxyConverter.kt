package com.subedit.core.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.subedit.core.mpv.MpvMediaHelper
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.max

object ChromaProxyConverter {
    private const val TAG = "ChromaProxyConverter"
    private const val PROXY_DIR_NAME = "chroma_proxy"
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val AVC_PROFILE_HIGH_422 = 122
    private const val AVC_PROFILE_HIGH_444 = 244

    data class VideoStreamInspection(
        val isYuv422: Boolean,
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val durationUs: Long,
        val videoTrackIndex: Int,
        val audioTrackIndex: Int?,
    )

    data class FileIdentity(
        val sizeBytes: Long,
        val lastModifiedMs: Long,
    )

    fun isAvcHigh422Sps(csd0Bytes: ByteArray): Boolean {
        if (csd0Bytes.size < 4) return false

        if (csd0Bytes[0].toInt() == 1) {
            val profileIdc = csd0Bytes[1].toInt() and 0xFF
            if (profileIdc == AVC_PROFILE_HIGH_422 || profileIdc == AVC_PROFILE_HIGH_444) {
                return true
            }
        }

        var i = 0
        val limit = csd0Bytes.size - 2
        while (i < limit) {
            if (csd0Bytes[i].toInt() == 0 && csd0Bytes[i + 1].toInt() == 0) {
                val nalIndex = when {
                    csd0Bytes[i + 2].toInt() == 1 -> i + 3
                    i + 3 < csd0Bytes.size && csd0Bytes[i + 2].toInt() == 0 && csd0Bytes[i + 3].toInt() == 1 -> i + 4
                    else -> -1
                }
                if (nalIndex in 0 until (csd0Bytes.size - 1)) {
                    val nalUnitType = csd0Bytes[nalIndex].toInt() and 0x1F
                    if (nalUnitType == 7) {
                        val profileIdc = csd0Bytes[nalIndex + 1].toInt() and 0xFF
                        if (profileIdc == AVC_PROFILE_HIGH_422 || profileIdc == AVC_PROFILE_HIGH_444) {
                            return true
                        }
                    }
                    i = nalIndex
                    continue
                }
            }
            i++
        }
        return false
    }

    fun downsampleYuv422PlanarToNv12(
        srcYuv422: ByteArray,
        dstNv12: ByteArray,
        width: Int,
        height: Int,
    ) {
        val frameSize = width * height
        val halfWidth = width / 2
        val halfHeight = height / 2
        if (srcYuv422.size < frameSize * 2 || dstNv12.size < frameSize + (frameSize / 2)) return

        System.arraycopy(srcYuv422, 0, dstNv12, 0, frameSize)

        val uOffset = frameSize
        val vOffset = frameSize + (halfWidth * height)
        var dstUvIndex = frameSize

        for (y in 0 until halfHeight) {
            val srcRow0 = (y * 2) * halfWidth
            val srcRow1 = minOf((y * 2 + 1), height - 1) * halfWidth
            for (x in 0 until halfWidth) {
                val u0 = srcYuv422[uOffset + srcRow0 + x].toInt() and 0xFF
                val u1 = srcYuv422[uOffset + srcRow1 + x].toInt() and 0xFF
                val v0 = srcYuv422[vOffset + srcRow0 + x].toInt() and 0xFF
                val v1 = srcYuv422[vOffset + srcRow1 + x].toInt() and 0xFF

                dstNv12[dstUvIndex++] = ((u0 + u1 + 1) shr 1).toByte()
                dstNv12[dstUvIndex++] = ((v0 + v1 + 1) shr 1).toByte()
            }
        }
    }

    fun buildProxyCacheFile(cacheDir: File, sizeBytes: Long, lastModifiedMs: Long): File {
        val proxyDir = File(cacheDir, PROXY_DIR_NAME).apply { mkdirs() }
        return File(proxyDir, "chroma420_${sizeBytes}_${lastModifiedMs}.mp4")
    }

    suspend fun resolveOrConvertVideoIfNeeded(
        context: Context,
        videoUri: Uri,
        onConversionProgress: (percent: Int, message: String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val inspection = inspectVideoStream(context, videoUri)
        if (inspection == null || !inspection.isYuv422) {
            return@withContext MpvMediaHelper.resolveUriToPath(context, videoUri)
        }

        val identity = resolveFileIdentity(context, videoUri)
        val proxyFile = buildProxyCacheFile(
            cacheDir = context.cacheDir,
            sizeBytes = identity.sizeBytes,
            lastModifiedMs = identity.lastModifiedMs,
        )

        if (proxyFile.exists() && proxyFile.length() > 1024L) {
            return@withContext proxyFile.absolutePath
        }

        onConversionProgress(0, "Mengonversi chroma 4:2:2 -> 4:2:0 (0%)...")
        val converted = runCatching {
            transcodeYuv422ToYuv420Hardware(
                context = context,
                videoUri = videoUri,
                inspection = inspection,
                targetFile = proxyFile,
                onProgress = { pct ->
                    onConversionProgress(pct, "Mengonversi chroma 4:2:2 -> 4:2:0 ($pct%)...")
                },
            )
        }.getOrElse { err ->
            Log.w(TAG, "Fallback ke dekoder software libmpv untuk video 4:2:2: ${err.message}")
            false
        }

        if (converted && proxyFile.exists() && proxyFile.length() > 1024L) {
            onConversionProgress(100, "Konversi chroma selesai (100%). Memuat frame perdana...")
            proxyFile.absolutePath
        } else {
            MpvMediaHelper.resolveUriToPath(context, videoUri)
        }
    }

    private fun inspectVideoStream(context: Context, uri: Uri): VideoStreamInspection? {
        val extractor = MediaExtractor()
        return try {
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: uri.toString())
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var videoTrack = -1
            var audioTrack: Int? = null
            var videoFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (videoTrack == -1 && mime.startsWith("video/")) {
                    videoTrack = i
                    videoFormat = fmt
                } else if (audioTrack == null && mime.startsWith("audio/")) {
                    audioTrack = i
                }
            }

            val fmt = videoFormat ?: return null
            val width = if (fmt.containsKey(MediaFormat.KEY_WIDTH)) fmt.getInteger(MediaFormat.KEY_WIDTH) else 1920
            val height = if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) fmt.getInteger(MediaFormat.KEY_HEIGHT) else 1080
            val frameRate = if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                runCatching { fmt.getInteger(MediaFormat.KEY_FRAME_RATE) }
                    .getOrElse { fmt.getFloat(MediaFormat.KEY_FRAME_RATE).toInt() }
                    .coerceIn(1, 240)
            } else {
                24
            }
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                fmt.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L)
            } else {
                0L
            }

            var is422 = false
            if (fmt.containsKey(MediaFormat.KEY_PROFILE)) {
                val profile = runCatching { fmt.getInteger(MediaFormat.KEY_PROFILE) }.getOrDefault(0)
                if (profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 ||
                    profile == AVC_PROFILE_HIGH_422 ||
                    profile == AVC_PROFILE_HIGH_444
                ) {
                    is422 = true
                }
            }

            if (!is422 && fmt.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                val colorFmt = runCatching { fmt.getInteger(MediaFormat.KEY_COLOR_FORMAT) }.getOrDefault(0)
                @Suppress("DEPRECATION")
                if (colorFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar ||
                    colorFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422SemiPlanar ||
                    colorFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedPlanar ||
                    colorFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar
                ) {
                    is422 = true
                }
            }

            if (!is422 && fmt.containsKey("csd-0")) {
                val csd0 = fmt.getByteBuffer("csd-0")
                if (csd0 != null) {
                    val dup = csd0.duplicate()
                    val bytes = ByteArray(dup.remaining())
                    dup.get(bytes)
                    if (isAvcHigh422Sps(bytes)) {
                        is422 = true
                    }
                }
            }

            VideoStreamInspection(
                isYuv422 = is422,
                width = width,
                height = height,
                frameRate = frameRate,
                durationUs = durationUs,
                videoTrackIndex = videoTrack,
                audioTrackIndex = audioTrack,
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun resolveFileIdentity(context: Context, uri: Uri): FileIdentity {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                return FileIdentity(file.length(), file.lastModified())
            }
        }

        var sizeBytes = 0L
        var lastModifiedMs = 0L

        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val realPath = runCatching { Utils.findRealPath(pfd.fd) }.getOrNull()
                if (!realPath.isNullOrEmpty()) {
                    val realFile = File(realPath)
                    if (realFile.exists()) {
                        return FileIdentity(realFile.length(), realFile.lastModified())
                    }
                }
                if (pfd.statSize > 0L) {
                    sizeBytes = pfd.statSize
                }
            }
        }

        runCatching {
            val projection = arrayOf(
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                        lastModifiedMs = cursor.getLong(modIdx)
                    }
                }
            }
        }

        if (sizeBytes <= 0L && lastModifiedMs <= 0L) {
            sizeBytes = uri.toString().hashCode().toLong() and 0xFFFFFFFFL
        }
        return FileIdentity(sizeBytes = sizeBytes, lastModifiedMs = lastModifiedMs)
    }

    private suspend fun transcodeYuv422ToYuv420Hardware(
        context: Context,
        videoUri: Uri,
        inspection: VideoStreamInspection,
        targetFile: File,
        onProgress: (Int) -> Unit,
    ): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            if (videoUri.scheme == "file") {
                extractor.setDataSource(videoUri.path ?: videoUri.toString())
            } else {
                extractor.setDataSource(context, videoUri, null)
            }

            val inputVideoFormat = extractor.getTrackFormat(inspection.videoTrackIndex)
            val inputMime = inputVideoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            val width = inspection.width
            val height = inspection.height
            val frameRate = inspection.frameRate
            val bitrate = if (inputVideoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                inputVideoFormat.getInteger(MediaFormat.KEY_BIT_RATE).coerceAtLeast(4_000_000)
            } else {
                max(6_000_000, width * height * 4)
            }

            val outputVideoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val matchingDecoderName = codecList.codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(inputMime, ignoreCase = true) } }
                .sortedBy { info -> if (info.name.contains("c2.android") || info.name.contains("google") || info.name.contains("ffmpeg")) 0 else 1 }
                .firstOrNull()
                ?.name

            decoder = (if (matchingDecoderName != null) {
                MediaCodec.createByCodecName(matchingDecoderName)
            } else {
                MediaCodec.createDecoderByType(inputMime)
            }).apply {
                configure(inputVideoFormat, null, null, 0)
                start()
            }

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerAudioTrack = -1
            val audioTrackIndex = inspection.audioTrackIndex
            if (audioTrackIndex != null) {
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                muxerAudioTrack = runCatching { muxer.addTrack(audioFormat) }.getOrDefault(-1)
            }

            extractor.selectTrack(inspection.videoTrackIndex)
            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()
            var muxerVideoTrack = -1
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var lastReportedPct = -1
            val nv12Scratch = ByteArray(width * height * 3 / 2)

            while (!encoderDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                val flags = extractor.sampleFlags
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, flags)
                                extractor.advance()
                            }
                        }
                    }
                }

                if (!decoderDone) {
                    val decOutIdx = decoder.dequeueOutputBuffer(decBufferInfo, CODEC_TIMEOUT_US)
                    if (decOutIdx >= 0) {
                        val isEos = (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val decOutBuf = decoder.getOutputBuffer(decOutIdx)
                        if (decOutBuf != null && decBufferInfo.size > 0) {
                            val encInIdx = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                            if (encInIdx >= 0) {
                                val encInBuf = encoder.getInputBuffer(encInIdx)
                                if (encInBuf != null) {
                                    encInBuf.clear()
                                    decOutBuf.position(decBufferInfo.offset)
                                    decOutBuf.limit(decBufferInfo.offset + decBufferInfo.size)

                                    val frameSize = width * height
                                    val expected420Size = frameSize * 3 / 2
                                    val expected422Size = frameSize * 2

                                    if (decBufferInfo.size >= expected422Size && encInBuf.remaining() >= expected420Size) {
                                        val raw422 = ByteArray(decBufferInfo.size)
                                        decOutBuf.get(raw422)
                                        downsampleYuv422PlanarToNv12(raw422, nv12Scratch, width, height)
                                        encInBuf.put(nv12Scratch, 0, expected420Size)
                                        encoder.queueInputBuffer(
                                            encInIdx,
                                            0,
                                            expected420Size,
                                            decBufferInfo.presentationTimeUs,
                                            if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0,
                                        )
                                    } else {
                                        val copyLen = minOf(decOutBuf.remaining(), encInBuf.remaining())
                                        val oldLimit = decOutBuf.limit()
                                        decOutBuf.limit(decOutBuf.position() + copyLen)
                                        encInBuf.put(decOutBuf)
                                        decOutBuf.limit(oldLimit)
                                        encoder.queueInputBuffer(
                                            encInIdx,
                                            0,
                                            copyLen,
                                            decBufferInfo.presentationTimeUs,
                                            if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0,
                                        )
                                    }
                                }
                            }
                        } else if (isEos) {
                            val encInIdx = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(
                                    encInIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                            }
                        }
                        decoder.releaseOutputBuffer(decOutIdx, false)
                        if (isEos) {
                            decoderDone = true
                        }
                    }
                }

                val encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, CODEC_TIMEOUT_US)
                when {
                    encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    encOutIdx >= 0 -> {
                        val encOutBuf = encoder.getOutputBuffer(encOutIdx)
                        val isCodecConfig = (encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (encOutBuf != null && encBufferInfo.size > 0 && !isCodecConfig && muxerStarted) {
                            encOutBuf.position(encBufferInfo.offset)
                            encOutBuf.limit(encBufferInfo.offset + encBufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encOutBuf, encBufferInfo)

                            if (inspection.durationUs > 0L) {
                                val pct = ((encBufferInfo.presentationTimeUs * 92L) / inspection.durationUs)
                                    .toInt()
                                    .coerceIn(0, 92)
                                if (pct >= lastReportedPct + 2) {
                                    lastReportedPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(encOutIdx, false)
                        if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            if (muxerStarted && audioTrackIndex != null && muxerAudioTrack >= 0) {
                extractor.unselectTrack(inspection.videoTrackIndex)
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val audioBuf = ByteBuffer.allocateDirect(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    coroutineContext.ensureActive()
                    audioBuf.clear()
                    val sampleSize = extractor.readSampleData(audioBuf, 0)
                    if (sampleSize < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                    audioInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, audioBuf, audioInfo)
                    extractor.advance()
                }
            }

            onProgress(100)
            muxer.stop()
            muxerStarted = false

            if (tempFile.exists() && tempFile.length() > 1024L) {
                if (targetFile.exists()) targetFile.delete()
                return tempFile.renameTo(targetFile)
            }
            return false
        } catch (t: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            throw t
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
