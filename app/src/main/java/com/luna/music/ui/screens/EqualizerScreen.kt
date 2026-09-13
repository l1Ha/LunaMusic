package com.luna.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luna.music.playback.AudioFx
import com.luna.music.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val ready by AudioFx.ready.collectAsState()

    var enabled by remember { mutableStateOf(false) }
    var levels by remember { mutableStateOf(listOf<Int>()) }
    var presets by remember { mutableStateOf(listOf<String>()) }
    var selectedPreset by remember { mutableIntStateOf(-1) }
    var bass by remember { mutableIntStateOf(0) }
    var virtual by remember { mutableIntStateOf(0) }
    var presetMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(ready) {
        if (ready) {
            enabled = AudioFx.enabled
            levels = AudioFx.bandLevels()
            presets = AudioFx.presetNames()
            bass = AudioFx.bassStrength()
            virtual = AudioFx.virtualizerStrength()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("音效") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        if (!ready) {
            EmptyState(
                icon = Icons.Rounded.GraphicEq,
                title = "均衡器尚未就绪",
                subtitle = "均衡器跟随系统音频会话工作\n先播放任意一首音乐，再回到本页",
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用音效", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            AudioFx.enabled = it
                            enabled = it
                        },
                    )
                }
                Text(
                    "对当前播放会话实时生效（预设 + 自定义频段 + 低音增强 + 环绕声）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { presetMenuOpen = true }) {
                        Text(if (selectedPreset >= 0 && selectedPreset < presets.size) presets[selectedPreset] else "自定义")
                    }
                    DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                        presets.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                trailingIcon = {
                                    if (index == selectedPreset) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    AudioFx.applyPreset(index)
                                    selectedPreset = index
                                    levels = AudioFx.bandLevels()
                                    presetMenuOpen = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                levels.forEachIndexed { band, level ->
                    var label by remember(band, ready) { mutableStateOf(AudioFx.bandCenterFreqLabel(band)) }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(56.dp),
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { value ->
                                val v = value.toInt()
                                levels = levels.toMutableList().also { it[band] = v }
                                selectedPreset = -1
                                AudioFx.setBandLevel(band, v)
                                if (!enabled) {
                                    AudioFx.enabled = true
                                    enabled = true
                                }
                            },
                            valueRange = -1500f..1500f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+d dB".format(level / 100),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(64.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("低音增强", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = bass.toFloat(),
                    onValueChange = {
                        bass = it.toInt()
                        AudioFx.setBassStrength(bass)
                        if (!enabled) {
                            AudioFx.enabled = true
                            enabled = true
                        }
                    },
                    valueRange = 0f..100f,
                )

                Text("环绕声", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = virtual.toFloat(),
                    onValueChange = {
                        virtual = it.toInt()
                        AudioFx.setVirtualizerStrength(virtual)
                        if (!enabled) {
                            AudioFx.enabled = true
                            enabled = true
                        }
                    },
                    valueRange = 0f..100f,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
