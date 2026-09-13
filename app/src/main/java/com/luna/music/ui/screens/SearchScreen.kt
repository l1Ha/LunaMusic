package com.luna.music.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luna.music.MainViewModel
import com.luna.music.data.Song
import com.luna.music.data.formatDuration
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.components.EmptyState
import com.luna.music.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onAction: (Song) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val allSongs by vm.songs.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val currentSong by PlayerBridge.currentSong.collectAsState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = query.trim()
    val songResults = remember(allSongs, trimmed) {
        if (trimmed.isEmpty()) emptyList()
        else allSongs.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true) ||
                it.album.contains(trimmed, ignoreCase = true)
        }.take(100)
    }
    val albumResults = remember(allSongs, trimmed) {
        if (trimmed.isEmpty()) emptyList()
        else com.luna.music.data.MediaRepository.albumsOf(allSongs)
            .filter { it.name.contains(trimmed, ignoreCase = true) || it.artist.contains(trimmed, ignoreCase = true) }
            .take(10)
    }
    val artistResults = remember(allSongs, trimmed) {
        if (trimmed.isEmpty()) emptyList()
        else com.luna.music.data.MediaRepository.artistsOf(allSongs)
            .filter { it.name.contains(trimmed, ignoreCase = true) }
            .take(10)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("搜索歌曲、专辑、歌手") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空")
                            }
                        }
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        if (trimmed.isEmpty()) {
            EmptyState(Icons.Rounded.Search, "搜索本地音乐", "输入关键词，支持歌曲 / 专辑 / 歌手")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (albumResults.isNotEmpty()) {
                    item { SectionHeader("专辑") }
                    items(albumResults, key = { "album_${it.id}" }) { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAlbum(album.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "专辑 · ${album.artist}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (artistResults.isNotEmpty()) {
                    item { SectionHeader("歌手") }
                    items(artistResults, key = { "artist_${it.id}" }) { artist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenArtist(artist.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "歌手 · ${artist.songCount} 首歌曲",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (songResults.isNotEmpty()) {
                    item { SectionHeader("歌曲") }
                    items(songResults, key = { "song_${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            isFavorite = song.id in favorites,
                            onClick = { PlayerBridge.playQueue(songResults, songResults.indexOf(song)) },
                            onAction = { onAction(song) },
                            onFavoriteToggle = { vm.toggleFavorite(song.id) },
                        )
                    }
                }
                if (songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, "没有找到「$trimmed」", "换个关键词试试") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
