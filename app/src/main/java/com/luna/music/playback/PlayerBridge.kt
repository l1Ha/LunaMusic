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
import kotlinx.coroutines.withContext

fun Song.toMediaItem(context: Context): MediaItem {
    val md = MediaMetadata.Builder()
    md.setTitle(title)
    md.setArtist(artist)
    md.setAlbumTitle(album)
    md.setArtworkUri(artworkUri)
    md.setDurationMs(durationMs)
    val builder = MediaItem.Builder()
    builder.setMediaId(id.toString())
    builder.setUri(TranscodeManager.playableUri(context, this))
    builder.setMediaMetadata(md.build())
    return builder.build()
}

object PlayerBridge {

    private const val TAG = "PlayerBridge"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    private var stateStore: PlaybackStateStore? = null
    private var autoSaveJob: Job? = null

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()

    private val _stopAfterCurrent = MutableStateFlow(false)
    val stopAfterCurrent: StateFlow<Boolean> = _stopAfterCurrent.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var pendingQueue: Pair<List<Song>, Int>? = null
    private var sleepJob: Job? = null

    fun clearError() {
        _error.value = null
    }

    fun clearToast() {
        _toast.value = null
    }

    fun currentPosition(): Long = _controller.value?.currentPosition ?: 0L

    fun currentDuration(): Long {
        val c = _controller.value ?: return 0L
        val d = c.duration
        if (d > 0) return d
        return _currentSong.value?.durationMs ?: 0L
    }

    fun connect(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            stateStore = PlaybackStateStore(context.applicationContext)
        }
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
                    startAutoSave()
                    restoreSavedSpeed()
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
            if (!isPlaying) persistState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshCurrent()
            persistState()
            if (_stopAfterCurrent.value) {
                _stopAfterCurrent.value = false
                _controller.value?.pause()
            }
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
                hasNext -> "该曲目无法播放（可能是 WMA 等特殊格式），已自动切换下一首"
                else -> "该曲目无法播放，建议检查文件是否损坏"
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
        val idx = c.currentMediaItemIndex
        _currentIndex.value = idx
        _currentSong.value = _queue.value.getOrNull(idx)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val c = _controller.value
        if (c == null) {
            pendingQueue = songs to startIndex
            return
        }
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val start = songs[safeIndex]
        val ctx = appContext
        if (ctx != null && TranscodeManager.isUnsupported(start) && !TranscodeManager.hasCache(ctx, start)) {
            _toast.value = "正在转换 ${start.title}（WMA/APE → AAC），完成后自动播放…"
            scope.launch {
                val file = withContext(Dispatchers.IO) { TranscodeManager.transcode(ctx, start) }
                if (file != null) {
                    playQueueNow(songs, safeIndex)
                } else {
                    _toast.value = "转码失败，无法播放 ${start.title}"
                }
            }
            return
        }
        playQueueNow(songs, safeIndex)
    }

    private fun playQueueNow(songs: List<Song>, startIndex: Int) {
        val c = _controller.value ?: return
        val ctx = appContext ?: return
        _queue.value = songs
        c.setMediaItems(songs.map { it.toMediaItem(ctx) }, startIndex, 0L)
        c.prepare()
        c.play()
        refreshCurrent()
        persistState()
    }

    fun playNext(song: Song) {
        val c = _controller.value
        if (c == null || _queue.value.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        val ctx = appContext ?: return
        if (TranscodeManager.isUnsupported(song) && !TranscodeManager.hasCache(ctx, song)) {
            _toast.value = "正在转换 ${song.title}…完成后插入队列"
            scope.launch {
                val file = withContext(Dispatchers.IO) { TranscodeManager.transcode(ctx, song) }
                if (file != null) insertNext(song)
                else _toast.value = "转码失败，无法加入 ${song.title}"
            }
            return
        }
        insertNext(song)
    }

    private fun insertNext(song: Song) {
        val c = _controller.value ?: return
        val ctx = appContext ?: return
        val insertIndex = c.currentMediaItemIndex + 1
        c.addMediaItems(insertIndex, listOf(song.toMediaItem(ctx)))
        _queue.value = _queue.value.toMutableList().also {
            it.add(insertIndex.coerceAtMost(it.size), song)
        }
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
        scope.launch { stateStore?.saveSpeed(speed) }
    }

    fun setStopAfterCurrent(enabled: Boolean) {
        _stopAfterCurrent.value = enabled
    }

    fun clearQueue() {
        val c = _controller.value ?: return
        c.stop()
        c.clearMediaItems()
        _queue.value = emptyList()
        _currentSong.value = null
        _currentIndex.value = 0
        scope.launch { stateStore?.clear() }
    }

    fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()
        setStopAfterCurrent(false)
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

    // ---------- 断点续播 ----------

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (true) {
                delay(5_000)
                if (_isPlaying.value) persistState()
            }
        }
    }

    private fun persistState() {
        val c = _controller.value ?: return
        val q = _queue.value
        if (q.isEmpty()) return
        scope.launch {
            stateStore?.save(
                queueIds = q.map { it.id },
                index = c.currentMediaItemIndex,
                positionMs = c.currentPosition,
                speed = _speed.value,
            )
        }
    }

    private fun restoreSavedSpeed() {
        scope.launch {
            val saved = stateStore?.load() ?: return@launch
            if (saved.speed != 1f) {
                _controller.value?.playbackParameters = PlaybackParameters(saved.speed)
            }
        }
    }

    fun restoreSavedQueue() {
        val c = _controller.value ?: return
        if (_queue.value.isNotEmpty()) return
        scope.launch {
            val saved = stateStore?.load() ?: return@launch
            val songs = MediaRepository.songsByIds(saved.queueIds)
            if (songs.isEmpty()) return@launch
            val ctx = appContext ?: return@launch
            _queue.value = songs
            val index = saved.index.coerceIn(0, songs.lastIndex)
            c.setMediaItems(songs.map { it.toMediaItem(ctx) }, index, saved.positionMs)
            c.prepare()
            if (saved.speed != 1f) {
                c.playbackParameters = PlaybackParameters(saved.speed)
                _speed.value = saved.speed
            }
            refreshCurrent()
        }
    }
}