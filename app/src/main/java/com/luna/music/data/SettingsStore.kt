package com.luna.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "luna_settings")

@Serializable
private data class PlaylistsData(val playlists: List<Playlist> = emptyList())

class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val SETTINGS = stringPreferencesKey("app_settings_json")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val PLAYLISTS = stringPreferencesKey("playlists_json")
    }

    suspend fun loadSettings(): AppSettings =
        context.dataStore.data.first()[Keys.SETTINGS]
            ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?: AppSettings()

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { it[Keys.SETTINGS] = json.encodeToString(settings) }
    }

    suspend fun loadFavorites(): Set<Long> =
        context.dataStore.data.first()[Keys.FAVORITES]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    suspend fun saveFavorites(favorites: Set<Long>) {
        context.dataStore.edit { it[Keys.FAVORITES] = favorites.map { id -> id.toString() }.toSet() }
    }

    suspend fun loadPlaylists(): List<Playlist> =
        context.dataStore.data.first()[Keys.PLAYLISTS]
            ?.let { runCatching { json.decodeFromString<PlaylistsData>(it) }.getOrNull()?.playlists }
            ?: emptyList()

    suspend fun savePlaylists(playlists: List<Playlist>) {
        context.dataStore.edit { it[Keys.PLAYLISTS] = json.encodeToString(PlaylistsData(playlists)) }
    }
}
