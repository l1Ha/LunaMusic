package com.luna.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 播放事件日志（内存环形缓冲）：
 * 记录暂停/恢复的系统原因、播放错误码、转码事件等，用于排查"播放暂停/停止"类问题。
 * 设置页 → 播放日志 可查看并复制。
 */
object PlaybackEventLog {

    private const val MAX_ENTRIES = 150

    data class Entry(val at: Long, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val entry = Entry(System.currentTimeMillis(), message)
        synchronized(this) {
            _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        }
    }

    fun asText(): String = synchronized(this) {
        _entries.value.joinToString("\n") { "${timeFormat.format(Date(it.at))}  ${it.message}" }
    }

    fun formatTime(at: Long): String = timeFormat.format(Date(at))

    fun clear() {
        synchronized(this) { _entries.value = emptyList() }
    }
}
