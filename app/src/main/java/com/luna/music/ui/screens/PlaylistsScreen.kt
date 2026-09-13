package com.luna.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luna.music.MainViewModel
import com.luna.music.data.Playlist
import com.luna.music.data.Song
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.components.EmptyState
import com.luna.music.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    vm: MainViewModel,
    favoritesCount: Int,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    val playlists by vm.playlists.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("播放列表") },
            actions = {
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, contentDescription = "新建") }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenFavorites)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("我喜欢的音乐", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "$favoritesCount 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlaylist(playlist.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${playlist.songIds.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (playlists.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "还没有播放列表",
                        subtitle = "点击右上角 + 新建\n长按任意歌曲即可添加",
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                vm.createPlaylist(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建播放列表") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("播放列表名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    vm: MainViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    onAction: (Song) -> Unit,
) {
    val playlists by vm.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val allSongs by vm.songs.collectAsState()
    val songs = remember(playlist, allSongs) {
        playlist?.songIds?.mapNotNull { id -> allSongs.firstOrNull { it.id == id } } ?: emptyList()
    }
    val favorites by vm.favorites.collectAsState()
    val currentSong by PlayerBridge.currentSong.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist?.name ?: "播放列表") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                if (playlist != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除播放列表")
                    }
                }
            },
        )
        if (songs.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "列表为空", "长按歌曲 → 加入播放列表")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "共 ${songs.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "播放全部",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { PlayerBridge.playQueue(songs, 0) },
                        )
                    }
                }
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isFavorite = song.id in favorites,
                        onClick = { PlayerBridge.playQueue(songs, songs.indexOf(song)) },
                        onAction = { onAction(song) },
                        onFavoriteToggle = { vm.toggleFavorite(song.id) },
                        trailingRemove = { vm.removeFromPlaylist(playlistId, song.id) },
                    )
                }
            }
        }
    }

    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除播放列表") },
            text = { Text("确定删除「${playlist.name}」吗？列表中的歌曲文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(playlist.id)
                    confirmDelete = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onAction: (Song) -> Unit,
) {
    val allSongs by vm.songs.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val currentSong by PlayerBridge.currentSong.collectAsState()
    val favoriteSongs = remember(allSongs, favorites) {
        allSongs.filter { it.id in favorites }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我喜欢的音乐") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        if (favoriteSongs.isEmpty()) {
            EmptyState(Icons.Rounded.Favorite, "还没有喜欢的音乐", "在歌曲列表点击 ❤ 收藏歌曲")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "共 ${favoriteSongs.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "播放全部",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { PlayerBridge.playQueue(favoriteSongs, 0) },
                        )
                    }
                }
                items(favoriteSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isFavorite = true,
                        onClick = { PlayerBridge.playQueue(favoriteSongs, favoriteSongs.indexOf(song)) },
                        onAction = { onAction(song) },
                        onFavoriteToggle = { vm.toggleFavorite(song.id) },
                    )
                }
            }
        }
    }
}
