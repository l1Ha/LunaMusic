package com.luna.music.playback

import android.content.Context
import android.net.Uri
import com.luna.music.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File

/**
 * 为系统无法解码的格式（WMA / APE / WavPack）提供“本地转码 → AAC → 缓存播放”支持。
 * 使用 JavaCV（FFmpeg）做解复用 + 解码 + 编码。
 */
object TranscodeManager {

    private val unsupportedExtensions = setOf("wma", "ape", "wv")

    fun isUnsupported(song: Song): Boolean =
        song.path.substringAfterLast('.', "").lowercase() in unsupportedExtensions

    private val _activeIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeIds: StateFlow<Set<Long>> = _activeIds.asStateFlow()

    fun cacheDir(context: Context): File = File(context.cacheDir, "transcoded").apply { mkdirs() }

    fun cachedFile(context: Context, song: Song): File = File(cacheDir(context), "${song.id}.m4a")

    fun hasCache(context: Context, song: Song): Boolean =
        cachedFile(context, song).let { it.isFile && it.length() > 0 }

    /** 返回该歌曲可直接交给播放器的 Uri：普通格式用 MediaStore 内容 Uri，特殊格式用转码缓存文件 Uri。 */
    fun playableUri(context: Context, song: Song): Uri =
        if (isUnsupported(song)) Uri.fromFile(cachedFile(context, song))
        else com.luna.music.data.MediaRepository.uriFor(song.id)

    /** 同步转码（须在 IO 线程调用）。成功返回非空缓存文件，失败返回 null。 */
    fun transcode(context: Context, song: Song): File? {
        val output = cachedFile(context, song)
        if (output.isFile && output.length() > 0) return output
        val tmp = File(cacheDir(context), "${song.id}.part.m4a")
        var grabber: FFmpegFrameGrabber? = null
        var recorder: FFmpegFrameRecorder? = null
        return try {
            _activeIds.value = _activeIds.value + song.id
            tmp.delete()

            grabber = FFmpegFrameGrabber(song.path)
            grabber.start()

            val channels = grabber.audioChannels.coerceIn(1, 2)
            val sampleRate = grabber.sampleRate.takeIf { it > 0 } ?: 44_100

            recorder = FFmpegFrameRecorder(tmp.absolutePath, channels)
            recorder.setFormat("m4a")
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC)
            recorder.setSampleRate(sampleRate)
            recorder.setAudioChannels(channels)
            recorder.setAudioBitrate(192_000)
            recorder.start()

            var frame: Frame? = grabber.grabFrame()
            while (frame != null) {
                if (frame.samples != null) {
                    recorder.record(frame)
                }
                frame = grabber.grabFrame()
            }

            val ok = tmp.isFile && tmp.length() > 0
            if (ok) {
                tmp.renameTo(output)
                output.takeIf { it.length() > 0 }
            } else {
                tmp.delete()
                null
            }
        } catch (_: Exception) {
            tmp.delete()
            null
        } finally {
            runCatching { recorder?.stop() }
            runCatching { grabber?.stop() }
            runCatching { recorder?.release() }
            runCatching { grabber?.release() }
            _activeIds.value = _activeIds.value - song.id
        }
    }
}