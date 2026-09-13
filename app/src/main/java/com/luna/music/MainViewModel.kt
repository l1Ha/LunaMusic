package com.luna.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luna.music.data.AppSettings
import com.luna.music.data.MediaRepository
import com.luna.music.data.Playlist
import com.luna.music.data.SettingsStore
import com.luna.music.data.Song
import com.luna.music.data.sortSongs
import com.luna.music.playback.PlayerBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val songs = MediaRepository.songs
    val isScanning = MediaRepository.isScanning

    init {
        viewModelScope.launch {
            _settings.value = store.loadSettings()
            _favorites.value = store.loadFavorites()
            _playlists.value = store.loadPlaylists()
        }
    }

    fun onPermissionGranted() {
        if (_hasPermission.value) return
        _hasPermission.value = true
        viewModelScope.launch { MediaRepository.scan(getApplication()) }
    }

    fun rescan() {
        viewModelScope.launch { MediaRepository.scan(getApplication()) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        viewModelScope.launch { store.saveSettings(next) }
    }

    fun toggleFavorite(songId: Long) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(songId)) next.remove(songId)
        _favorites.value = next
        viewModelScope.launch { store.saveFavorites(next) }
    }

    fun isFavorite(songId: Long): Boolean = songId in _favorites.value

    fun favoriteSongs(songs: List<Song>): List<Song> =
        songs.filter { it.id in _favorites.value }

    fun sorted(songs: List<Song>): List<Song> {
        val s = _settings.value
        return sortSongs(songs, s.sortMode, s.sortAsc)
    }

    fun createPlaylist(name: String): Playlist {
        val id = (_playlists.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val playlist = Playlist(id = id, name = name)
        _playlists.value = _playlists.value + playlist
        persistPlaylists()
        return playlist
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId && songId !in pl.songIds) {
                pl.copy(songIds = pl.songIds + songId)
            } else {
                pl
            }
        }
        persistPlaylists()
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(songIds = pl.songIds - songId) else pl
        }
        persistPlaylists()
    }

    fun deletePlaylist(playlistId: Long) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        persistPlaylists()
    }

    private fun persistPlaylists() {
        viewModelScope.launch { store.savePlaylists(_playlists.value) }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        PlayerBridge.playQueue(songs, startIndex)
    }
}
