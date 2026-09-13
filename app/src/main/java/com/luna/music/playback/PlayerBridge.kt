package com.luna.music.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.luna.music.data.MediaRepository
import com.luna.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun Song.toMediaItem(): MediaItem {
    val md = MediaMetadata.Builder()
    md.setTitle(title)
    md.setArtist(artist)
    md.setAlbumTitle(album)
    md.setArtworkUri(artworkUri)
    md.setDurationMs(durationMs)
    val builder = MediaItem.Builder()
    builder.setMediaId(id.toString())
    builder.setUri(MediaRepository.uriFor(id))
    builder.setMediaMetadata(md.build())
    return builder.build()
}

object PlayerBridge {

    private const val TAG = "PlayerBridge"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    private var pendingQueue: Pair<List<Song>, Int>? = null
    private var sleepJob: Job? = null

    val currentIndex: Int
        get() = _controller.value?.currentMediaItemIndex ?: 0

    val currentSpeed: Float
        get() = _controller.value?.playbackParameters?.speed ?: 1f

    fun currentPosition(): Long = _controller.value?.currentPosition ?: 0L

    fun currentDuration(): Long {
        val c = _controller.value ?: return 0L
        val d = c.duration
        if (d > 0) return d
        return _currentSong.value?.durationMs ?: 0L
    }

    fun connect(context: Context) {
        if (_controller.value != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                try {
                    val c = future.get()
                    c.addListener(listener)
                    _controller.value = c
                    _isPlaying.value = c.isPlaying
                    _shuffleEnabled.value = c.shuffleModeEnabled
                    _repeatMode.value = c.repeatMode
                    _speed.value = c.playbackParameters.speed
                    refreshCurrent()
                    pendingQueue?.let { (songs, index) ->
                        pendingQueue = null
                        playQueue(songs, index)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect MediaController", e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshCurrent()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _speed.value = playbackParameters.speed
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = _controller.value
            val hasNext = c != null && c.currentMediaItemIndex < c.mediaItemCount - 1
            _error.value = when {
                hasNext -> "该曲目无法播放（可能是 WMA 等设备不支持的格式），已自动切换下一首"
                else -> "该曲目无法播放（可能是 WMA 等设备不支持的格式），建议转换为 MP3/FLAC 后使用"
            }
            scope.launch {
                delay(600)
                if (hasNext) {
                    _controller.value?.seekToNextMediaItem()
                    _controller.value?.play()
                }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                refreshCurrent()
            }
        }
    }

    private fun refreshCurrent() {
        val c = _controller.value ?: return
        _currentSong.value = _queue.value.getOrNull(c.currentMediaItemIndex)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val c = _controller.value
        if (c == null) {
            pendingQueue = songs to startIndex
            return
        }
        if (songs.isEmpty()) return
        _queue.value = songs
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex), 0L)
        c.prepare()
        c.play()
        refreshCurrent()
    }

    fun toggle() {
        val c = _controller.value ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        _controller.value?.seekToNextMediaItem()
    }

    fun previous() {
        _controller.value?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs)
    }

    fun seekToIndex(index: Int) {
        _controller.value?.seekTo(index, 0L)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _controller.value?.shuffleModeEnabled = enabled
    }

    fun cycleRepeatMode() {
        val c = _controller.value ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        _controller.value?.playbackParameters = PlaybackParameters(speed)
    }

    fun clearQueue() {
        val c = _controller.value ?: return
        c.stop()
        c.clearMediaItems()
        _queue.value = emptyList()
        _currentSong.value = null
    }

    fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()
        sleepJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                _sleepRemainingMs.value = remaining
                delay(minOf(1000L, remaining))
                remaining -= 1000L
            }
            _sleepRemainingMs.value = null
            _controller.value?.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }

    fun formatSleepRemaining(ms: Long): String {
        val totalMin = ms / 60000
        val sec = (ms % 60000) / 1000
        return if (totalMin > 0) "${totalMin}分${sec}秒" else "${sec}秒"
    }
}
