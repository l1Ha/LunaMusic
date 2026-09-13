package com.luna.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luna.music.MainViewModel
import com.luna.music.data.Album
import com.luna.music.data.Artist
import com.luna.music.data.Song
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.components.CoverImage
import com.luna.music.ui.components.EmptyState
import com.luna.music.ui.components.SongRow

@Composable
fun AlbumsScreen(albums: List<Album>, onOpenAlbum: (Album) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.Album, "没有专辑", "扫描到音乐后会显示在这里")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpenAlbum(album) }
                    .padding(4.dp),
            ) {
                CoverImage(
                    model = album.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${album.artist} · ${album.songCount}首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    vm: MainViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onAction: (Song) -> Unit,
) {
    val allSongs by vm.songs.collectAsState()
    val albumSongs = remember(allSongs, albumId) {
        allSongs.filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
    }
    val album = remember(albumSongs, albumId) {
        albumSongs.firstOrNull()?.let {
            Album(id = albumId, name = it.album, artist = it.artist, songCount = albumSongs.size)
        }
    }
    val favorites by vm.favorites.collectAsState()
    val currentSong by PlayerBridge.currentSong.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(album?.name ?: "专辑") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(
                        model = albumSongs.firstOrNull()?.artworkUri,
                        modifier = Modifier.size(120.dp),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(album?.artist ?: "", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${albumSongs.size} 首 · ${albumSongs.sumOf { it.durationMs } / 60000} 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "播放全部",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable {
                                if (albumSongs.isNotEmpty()) PlayerBridge.playQueue(albumSongs, 0)
                            },
                        )
                    }
                }
            }
            if (albumSongs.isEmpty()) {
                item { EmptyState(Icons.Rounded.MusicNote, "专辑为空") }
            } else {
                items(albumSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isFavorite = song.id in favorites,
                        onClick = { PlayerBridge.playQueue(albumSongs, albumSongs.indexOf(song)) },
                        onAction = { onAction(song) },
                        onFavoriteToggle = { vm.toggleFavorite(song.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistsScreen(artists: List<Artist>, onOpenArtist: (Artist) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "没有歌手", "扫描到音乐后会显示在这里")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(artist) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    model = null,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${artist.songCount} 首歌曲 · ${artist.albumCount} 张专辑",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    vm: MainViewModel,
    artistId: Long,
    onBack: () -> Unit,
    onAction: (Song) -> Unit,
) {
    val allSongs by vm.songs.collectAsState()
    val artistSongs = remember(allSongs, artistId) {
        allSongs.filter { it.artistId == artistId }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.track }, { it.title.lowercase() }))
    }
    val artistName = artistSongs.firstOrNull()?.artist ?: "歌手"
    val favorites by vm.favorites.collectAsState()
    val currentSong by PlayerBridge.currentSong.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(artistName) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        if (artistSongs.isEmpty()) {
            EmptyState(Icons.Rounded.Person, "该歌手暂无歌曲")
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
                            "${artistSongs.size} 首 · ${artistSongs.groupBy { it.albumId }.size} 张专辑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "播放全部",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { PlayerBridge.playQueue(artistSongs, 0) },
                        )
                    }
                }
                items(artistSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isFavorite = song.id in favorites,
                        onClick = { PlayerBridge.playQueue(artistSongs, artistSongs.indexOf(song)) },
                        onAction = { onAction(song) },
                        onFavoriteToggle = { vm.toggleFavorite(song.id) },
                    )
                }
            }
        }
    }
}
