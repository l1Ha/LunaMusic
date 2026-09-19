package com.luna.music.playback

import android.content.Context
import android.net.Uri
import com.luna.music.data.MediaRepository
import com.luna.music.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * lite 诊断版的占位实现：不包含 FFmpeg 依赖，WMA / APE 不支持转码播放。
 * 与 full 版保持相同 API，供 PlayerBridge 无差别调用。
 */
object TranscodeManager {

    private val _activeIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeIds: StateFlow<Set<Long>> = _activeIds.asStateFlow()

    fun isUnsupported(song: Song): Boolean = false

    fun cacheDir(context: Context): File = File(context.cacheDir, "transcoded").apply { mkdirs() }

    fun cachedFile(context: Context, song: Song): File = File(cacheDir(context), "${song.id}.m4a")

    fun hasCache(context: Context, song: Song): Boolean = false

    fun playableUri(context: Context, song: Song): Uri = MediaRepository.uriFor(song.id)

    /** lite 版不支持转码。 */
    fun transcode(context: Context, song: Song): File? = null
}
