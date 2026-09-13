package com.luna.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioFx {

    private const val TAG = "AudioFx"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val isReady: Boolean
        get() = equalizer != null

    var enabled: Boolean
        get() = equalizer?.enabled ?: false
        set(value) {
            runCatching {
                equalizer?.enabled = value
                bassBoost?.enabled = value
                virtualizer?.enabled = value
            }
        }

    fun attach(player: Player) {
        val exo = player as? ExoPlayer ?: return
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                if (audioSessionId > 0) createEffects(audioSessionId)
            }
        })
        val existing = exo.audioSessionId
        if (existing > 0) createEffects(existing)
    }

    private fun createEffects(audioSessionId: Int) {
        runCatching {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = false }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = false }
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = false }
            _ready.value = true
        }.onFailure { Log.e(TAG, "Failed to create audio effects", it) }
    }

    fun detach() {
        runCatching {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        _ready.value = false
    }

    fun bandCount(): Int = runCatching { equalizer?.numberOfBands?.toInt() }.getOrNull() ?: 0

    fun bandLevels(): List<Int> =
        (0 until bandCount()).map { band ->
            runCatching { equalizer?.getBandLevel(band.toShort())?.toInt() }.getOrNull() ?: 0
        }

    fun setBandLevel(band: Int, levelMilliBel: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMilliBel.coerceIn(-1500, 1500).toShort()) }
    }

    fun bandCenterFreqLabel(band: Int): String {
        val milliHz = runCatching { equalizer?.getCenterFreq(band.toShort()) }.getOrNull() ?: (60 * 1000)
        val hz = milliHz / 1000
        return if (hz >= 1000) "%.1fkHz".format(hz / 1000f) else "${hz}Hz"
    }

    fun levelRange(): IntArray = runCatching {
        val r = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        intArrayOf(r[0].toInt(), r[1].toInt())
    }.getOrDefault(intArrayOf(-1500, 1500))

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        }.getOrDefault(emptyList())
    }

    fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(index.toShort()) }
    }

    fun bassStrength(): Int =
        runCatching { (bassBoost?.roundedStrength?.toInt() ?: 0) / 10 }.getOrDefault(0)

    fun setBassStrength(percent: Int) {
        runCatching { bassBoost?.setStrength((percent.coerceIn(0, 100) * 10).toShort()) }
    }

    fun virtualizerStrength(): Int =
        runCatching { (virtualizer?.roundedStrength?.toInt() ?: 0) / 10 }.getOrDefault(0)

    fun setVirtualizerStrength(percent: Int) {
        runCatching { virtualizer?.setStrength((percent.coerceIn(0, 100) * 10).toShort()) }
    }
}
