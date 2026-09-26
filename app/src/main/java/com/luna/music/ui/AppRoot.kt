package com.luna.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luna.music.MainViewModel
import com.luna.music.data.Song
import com.luna.music.data.sortSongs
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.components.CoverImage
import com.luna.music.ui.components.PermissionScreen
import com.luna.music.ui.components.SongRow
import com.luna.music.ui.screens.AlbumDetailScreen
import com.luna.music.ui.screens.AlbumsScreen
import com.luna.music.ui.screens.ArtistDetailScreen
import com.luna.music.ui.screens.ArtistsScreen
import com.luna.music.ui.screens.FavoritesScreen
import com.luna.music.ui.screens.FolderDetailScreen
import com.luna.music.ui.screens.FoldersScreen
import com.luna.music.ui.screens.NowPlayingScreen
import com.luna.music.ui.screens.PlaylistDetailScreen
import com.luna.music.ui.screens.PlaylistsScreen
import com.luna.music.ui.screens.PlaybackLogScreen
import com.luna.music.ui.screens.SearchScreen
import com.luna.music.ui.screens.ScanFoldersScreen
import com.luna.music.ui.screens.SettingsScreen
import com.luna.music.ui.screens.SongsScreen
import com.luna.music.ui.screens.EqualizerScreen
import kotlinx.coroutines.delay

sealed interface Detail {
    data class AlbumDetail(val albumId: Long) : Detail
    data class ArtistDetail(val artistId: Long) : Detail
    data class FolderDetail(val path: String) : Detail
    data class PlaylistDetail(val playlistId: Long) : Detail
    data object Favorites : Detail
    data object Search : Detail
    data object Settings : Detail
    data object Equalizer : Detail
    data object ScanFolders : Detail
    data object PlaybackLog : Detail
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val hasPermission by vm.hasPermission.collectAsState()
    if (!hasPermission) {
        PermissionScreen(onGranted = { vm.onPermissionGranted() })
        return
    }

    val songs by vm.songs.collectAsState()
    val settings by vm.settings.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()

    val sortedSongs = remember(songs, settings.sortMode, settings.sortAsc) {
        sortSongs(songs, settings.sortMode, settings.sortAsc)
    }
    val albums = remember(songs) { com.luna.music.data.MediaRepository.albumsOf(songs) }
    val artists = remember(songs) { com.luna.music.data.MediaRepository.artistsOf(songs) }
    val folders = remember(songs) { com.luna.music.data.MediaRepository.foldersOf(songs) }

    var tab by rememberSaveable { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val currentSong by PlayerBridge.currentSong.collectAsState()
    val isPlaying by PlayerBridge.isPlaying.collectAsState()
    val playbackError by PlayerBridge.error.collectAsState()
    val playbackToast by PlayerBridge.toast.collectAsState()
    val toastContext = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(playbackError) {
        playbackError?.let {
            android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_LONG).show()
            PlayerBridge.clearError()
        }
    }

    androidx.compose.runtime.LaunchedEffect(playbackToast) {
        playbackToast?.let {
            android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_LONG).show()
            PlayerBridge.clearToast()
        }
    }
    val positionMs by produceState(0L, currentSong?.id, isPlaying) {
        while (true) {
            value = PlayerBridge.currentPosition()
            delay(if (isPlaying) 300L else 1000L)
        }
    }
    val durationMs = PlayerBridge.currentDuration()

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && detail != null) { detail = null }

    fun openSongAction(song: Song) {
        actionSong = song
    }

    fun openDetail(d: Detail) {
        detail = d
        showPlayer = false
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (detail == null) {
                    Column {
                        currentSong?.let { song ->
                            MiniPlayer(
                                song = song,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                onClick = { showPlayer = true },
                                onToggle = { PlayerBridge.toggle() },
                                onNext = { PlayerBridge.next() },
                            )
                        }
                        NavigationBar {
                            val tabs = listOf("音乐", "专辑", "歌手", "文件夹", "列表")
                            val icons = listOf(
                                Icons.Rounded.MusicNote,
                                Icons.Rounded.Album,
                                Icons.Rounded.Person,
                                Icons.Rounded.Folder,
                                Icons.AutoMirrored.Rounded.QueueMusic,
                            )
                            tabs.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(icons[index], contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                Crossfade(targetState = detail, label = "detail") { d ->
                    when (d) {
                        null -> when (tab) {
                            0 -> SongsScreen(
                                vm = vm,
                                songs = sortedSongs,
                                onOpenSearch = { detail = Detail.Search },
                                onOpenSettings = { detail = Detail.Settings },
                                onOpenEqualizer = { detail = Detail.Equalizer },
                                onAction = ::openSongAction,
                            )
                            1 -> AlbumsScreen(
                                albums = albums,
                                onOpenAlbum = { openDetail(Detail.AlbumDetail(it.id)) },
                            )
                            2 -> ArtistsScreen(
                                artists = artists,
                                onOpenArtist = { openDetail(Detail.ArtistDetail(it.id)) },
                            )
                            3 -> FoldersScreen(
                                folders = folders,
                                onOpenFolder = { openDetail(Detail.FolderDetail(it.path)) },
                            )
                            else -> PlaylistsScreen(
                                vm = vm,
                                favoritesCount = favorites.size,
                                onOpenFavorites = { openDetail(Detail.Favorites) },
                                onOpenPlaylist = { openDetail(Detail.PlaylistDetail(it)) },
                            )
                        }
                        is Detail.AlbumDetail -> AlbumDetailScreen(
                            vm = vm,
                            albumId = d.albumId,
                            onBack = { detail = null },
                            onAction = ::openSongAction,
                        )
                        is Detail.ArtistDetail -> ArtistDetailScreen(
                            vm = vm,
                            artistId = d.artistId,
                            onBack = { detail = null },
                            onAction = ::openSongAction,
                        )
                        is Detail.FolderDetail -> FolderDetailScreen(
                            vm = vm,
                            folderPath = d.path,
                            onBack = { detail = null },
                            onAction = ::openSongAction,
                        )
                        is Detail.PlaylistDetail -> PlaylistDetailScreen(
                            vm = vm,
                            playlistId = d.playlistId,
                            onBack = { detail = null },
                            onAction = ::openSongAction,
                        )
                        Detail.Favorites -> FavoritesScreen(
                            vm = vm,
                            onBack = { detail = null },
                            onAction = ::openSongAction,
                        )
                        Detail.Search -> SearchScreen(
                            vm = vm,
                            onBack = { detail = null },
                            onOpenAlbum = { openDetail(Detail.AlbumDetail(it)) },
                            onOpenArtist = { openDetail(Detail.ArtistDetail(it)) },
                            onAction = ::openSongAction,
                        )
                        Detail.Settings -> SettingsScreen(
                            vm = vm,
                            onBack = { detail = null },
                            onOpenScanFolders = { openDetail(Detail.ScanFolders) },
                            onOpenPlaybackLog = { openDetail(Detail.PlaybackLog) },
                        )
                        Detail.Equalizer -> EqualizerScreen(
                            onBack = { detail = null },
                        )
                        Detail.ScanFolders -> ScanFoldersScreen(
                            vm = vm,
                            onBack = { detail = null },
                        )
                        Detail.PlaybackLog -> PlaybackLogScreen(
                            onBack = { detail = null },
                        )
                    }
                }
            }
        }

        currentSong?.let { song ->
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                NowPlayingScreen(
                    vm = vm,
                    song = song,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onClose = { showPlayer = false },
                    onOpenAlbum = { openDetail(Detail.AlbumDetail(song.albumId)) },
                )
            }
        }
    }

    actionSong?.let { song ->
        SongActionSheet(
            song = song,
            isFavorite = song.id in favorites,
            onDismiss = { actionSong = null },
            onToggleFavorite = { vm.toggleFavorite(song.id) },
            onAddToPlaylist = { showAddToPlaylist = true },
            onOpenAlbum = { openDetail(Detail.AlbumDetail(song.albumId)); actionSong = null },
            onOpenArtist = { openDetail(Detail.ArtistDetail(song.artistId)); actionSong = null },
        )
    }

    if (showAddToPlaylist) {
        val song = actionSong
        if (song != null) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { showAddToPlaylist = false },
                onCreateAndAdd = { name ->
                    val pl = vm.createPlaylist(name)
                    vm.addToPlaylist(pl.id, song.id)
                    showAddToPlaylist = false
                    actionSong = null
                },
                onPick = { playlistId ->
                    vm.addToPlaylist(playlistId, song.id)
                    showAddToPlaylist = false
                    actionSong = null
                },
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(song.artworkUri, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null)
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SongActionSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(song.artworkUri, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SheetAction(Icons.Rounded.PlayArrow, "播放") { PlayerBridge.playQueue(listOf(song), 0); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "下一首播放") { PlayerBridge.playNext(song); onDismiss() }
            SheetAction(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFavorite) "取消收藏" else "收藏",
            ) { onToggleFavorite(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "加入播放列表") { onAddToPlaylist(); onDismiss() }
            SheetAction(Icons.Rounded.Album, "查看专辑") { onOpenAlbum() }
            SheetAction(Icons.Rounded.Person, "查看歌手") { onOpenArtist() }
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistDialog(
    playlists: List<com.luna.music.data.Playlist>,
    onDismiss: () -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onPick: (Long) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "加入播放列表",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("新建播放列表") },
                    singleLine = true,
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = { if (newName.isNotBlank()) onCreateAndAdd(newName.trim()) },
                ) { Text("创建") }
            }
            Spacer(Modifier.height(8.dp))
            playlists.forEach { playlist ->
                SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, playlist.name) { onPick(playlist.id) }
            }
            if (playlists.isEmpty()) {
                Text(
                    "还没有播放列表",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}
