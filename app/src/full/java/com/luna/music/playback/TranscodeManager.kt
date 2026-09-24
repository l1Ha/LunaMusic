package com.luna.music.playback

import android.content.Context
import android.net.Uri
import com.luna.music.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** 单首转码的最长执行时间，防止损坏文件把整条播放队列永久卡死 */
    private const val MAX_TRANSCODE_MS = 180_000L

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

    /** 转码串行化：避免两个协程同时转码，写坏同一个 .part 文件 */
    private val transcodeMutex = Mutex()

    /**
     * 转码（挂起函数，内部串行执行）。成功返回非空缓存文件，失败返回 null。
     *
     * 关键约束：
     * - 必须先 recorder.stop() 写完 MP4 索引（moov），再把 .part 改名为正式缓存；
     *   否则遇到 stop 抛错时会留下未封装的 m4a，播放中途才报错。
     * - 设置最长执行时间并在解码循环里检查，损坏/畸形文件不能让整条队列卡死。
     */
    suspend fun transcode(context: Context, song: Song): File? {
        val output = cachedFile(context, song)
        if (output.isFile && output.length() > 0) return output
        return transcodeMutex.withLock {
            // 等锁期间可能已被其它协程转完
            if (output.isFile && output.length() > 0) return@withLock output
            doTranscode(context, song, output)
        }
    }

    private fun doTranscode(context: Context, song: Song, output: File): File? {
        val tmp = File(cacheDir(context), "${song.id}.part.m4a")
        var grabber: FFmpegFrameGrabber? = null
        var recorder: FFmpegFrameRecorder? = null
        _activeIds.value = _activeIds.value + song.id
        tmp.delete()
        val startAt = System.currentTimeMillis()
        try {
            grabber = FFmpegFrameGrabber(song.path)
            grabber.start()

            val channels = grabber.audioChannels.coerceIn(1, 2)
            val sampleRate = grabber.sampleRate.takeIf { it > 0 } ?: 44_100

            recorder = FFmpegFrameRecorder(tmp.absolutePath, channels)
            recorder.setFormat("m4a")
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC)
            recorder.setSampleRate(sampleRate)
            recorder.setAudioChannels(channels)
            // 256k：源文件（WMA/APE）本就有损，用高码率把代际损失压到听感以下
            recorder.setAudioBitrate(256_000)
            recorder.start()

            var frame: Frame? = grabber.grabFrame()
            while (frame != null) {
                if (System.currentTimeMillis() - startAt > MAX_TRANSCODE_MS) {
                    // 损坏文件导致解码异常缓慢：放弃，不让队列等着
                    return null
                }
                if (frame.samples != null) {
                    recorder.record(frame)
                }
                frame = grabber.grabFrame()
            }

            // 先让 muxer 收尾（写 moov 索引）并释放解码器，成功后才能交付缓存文件
            // 注意：stop 失败时保持引用，交给 finally 做兜底释放
            runCatching { recorder?.stop() }.onFailure { return null }
            runCatching { recorder?.release() }
            recorder = null
            runCatching { grabber?.stop() }
            runCatching { grabber?.release() }
            grabber = null

            if (!tmp.isFile || tmp.length() <= 1024) return null
            output.delete()
            if (!tmp.renameTo(output)) {
                tmp.copyTo(output, overwrite = true)
                tmp.delete()
            }
            return output.takeIf { it.isFile && it.length() > 1024 }
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { recorder?.stop() }
            runCatching { grabber?.stop() }
            runCatching { recorder?.release() }
            runCatching { grabber?.release() }
            if (!output.isFile || output.length() <= 1024) tmp.delete()
            _activeIds.value = _activeIds.value - song.id
        }
    }
}
