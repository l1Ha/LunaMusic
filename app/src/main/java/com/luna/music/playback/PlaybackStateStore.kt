package com.luna.music.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "luna_playback")

/**
 * 播放状态持久化：用于退出应用后恢复队列与进度（断点续播）。
 */
class PlaybackStateStore(private val context: Context) {

    data class SavedState(
        val queueIds: List<Long>,
        val index: Int,
        val positionMs: Long,
        val speed: Float,
    )

    private object Keys {
        val QUEUE_IDS = stringPreferencesKey("queue_ids")
        val INDEX = intPreferencesKey("queue_index")
        val POSITION = longPreferencesKey("position_ms")
        val SPEED = floatPreferencesKey("speed")
    }

    suspend fun save(queueIds: List<Long>, index: Int, positionMs: Long, speed: Float) {
        context.playbackDataStore.edit {
            it[Keys.QUEUE_IDS] = queueIds.joinToString(",")
            it[Keys.INDEX] = index
            it[Keys.POSITION] = positionMs
            it[Keys.SPEED] = speed
        }
    }

    suspend fun saveSpeed(speed: Float) {
        context.playbackDataStore.edit { it[Keys.SPEED] = speed }
    }

    suspend fun load(): SavedState? {
        val prefs = context.playbackDataStore.data.first()
        val ids = prefs[Keys.QUEUE_IDS]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null
        return SavedState(
            queueIds = ids,
            index = prefs[Keys.INDEX] ?: 0,
            positionMs = prefs[Keys.POSITION] ?: 0L,
            speed = prefs[Keys.SPEED] ?: 1f,
        )
    }

    suspend fun clear() {
        context.playbackDataStore.edit { it.clear() }
    }
}
