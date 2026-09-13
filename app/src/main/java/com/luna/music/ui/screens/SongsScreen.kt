package com.luna.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luna.music.MainViewModel
import com.luna.music.data.Song
import com.luna.music.data.SortMode
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.components.EmptyState
import com.luna.music.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    vm: MainViewModel,
    songs: List<Song>,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAction: (Song) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val favorites by vm.favorites.collectAsState()
    val settings by vm.settings.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val currentSong by PlayerBridge.currentSong.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("音乐") },
            actions = {
                IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, contentDescription = "搜索") }
                IconButton(onClick = onOpenEqualizer) { Icon(Icons.Rounded.Tune, contentDescription = "音效") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "设置") }
                IconButton(onClick = { sortMenuOpen = true }) { Icon(Icons.Rounded.ArrowDropDown, contentDescription = "排序") }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            trailingIcon = {
                                if (settings.sortMode == mode) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.width(20.dp))
                                }
                            },
                            onClick = {
                                vm.updateSettings { it.copy(sortMode = mode) }
                                sortMenuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (settings.sortAsc) "升序" else "降序") },
                        trailingIcon = {
                            Icon(
                                if (settings.sortAsc) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                            )
                        },
                        onClick = { vm.updateSettings { it.copy(sortAsc = !it.sortAsc) } },
                    )
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 ${songs.size} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = {
                if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    PlayerBridge.playQueue(shuffled, 0)
                }
            }) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("随机播放")
            }
        }

        if (songs.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = "还没有找到音乐",
                subtitle = "把音频文件放到设备存储后，下拉重新扫描\n（设置 → 重新扫描）",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isFavorite = song.id in favorites,
                        onClick = { PlayerBridge.playQueue(songs, songs.indexOf(song)) },
                        onAction = { onAction(song) },
                        onFavoriteToggle = { vm.toggleFavorite(song.id) },
                    )
                }
            }
        }
    }
}
