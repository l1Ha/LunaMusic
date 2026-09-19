package com.luna.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object MediaRepository {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    suspend fun scan(
        context: Context,
        folderFilter: List<String> = emptyList(),
        excludedFolders: List<String> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true
        try {
            _songs.value = querySongs(context, folderFilter, excludedFolders)
        } finally {
            _isScanning.value = false
        }
    }

    /** 列出（未过滤的）所有音频所在文件夹，供“指定扫描文件夹”使用。 */
    suspend fun discoverFolders(context: Context, excludedFolders: List<String> = emptyList()): List<Folder> =
        withContext(Dispatchers.IO) {
            foldersOf(querySongs(context, excludedFolders = excludedFolders))
        }

    private fun isExcluded(path: String, excludedFolders: List<String>): Boolean =
        excludedFolders.any { ex -> path == ex || path.startsWith(ex.trimEnd('/') + "/") }

    private fun querySongs(
        context: Context,
        folderFilter: List<String> = emptyList(),
        excludedFolders: List<String> = emptyList(),
    ): List<Song> {
        val result = mutableListOf<Song>()
        val collection =
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
        )
        val selection = buildString {
            append("(${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE '%.wma'")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE '%.ape'")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE '%.wv')")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= 30000")
            if (folderFilter.isNotEmpty()) {
                append(" AND (")
                folderFilter.forEachIndexed { index, path ->
                    if (index > 0) append(" OR ")
                    append("${MediaStore.Audio.Media.DATA} LIKE '${path.replace("'", "''")}%'")
                }
                append(")")
            }
        }
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            fun idx(name: String) = c.getColumnIndexOrThrow(name)
            val idC = idx(MediaStore.Audio.Media._ID)
            val titleC = idx(MediaStore.Audio.Media.TITLE)
            val artistC = idx(MediaStore.Audio.Media.ARTIST)
            val artistIdC = idx(MediaStore.Audio.Media.ARTIST_ID)
            val albumC = idx(MediaStore.Audio.Media.ALBUM)
            val albumIdC = idx(MediaStore.Audio.Media.ALBUM_ID)
            val durationC = idx(MediaStore.Audio.Media.DURATION)
            val sizeC = idx(MediaStore.Audio.Media.SIZE)
            val dateC = idx(MediaStore.Audio.Media.DATE_MODIFIED)
            val trackC = idx(MediaStore.Audio.Media.TRACK)
            val pathC = idx(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val path = c.getString(pathC) ?: continue
                if (isExcluded(path, excludedFolders)) continue
                result += Song(
                    id = c.getLong(idC),
                    title = c.getString(titleC)?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'),
                    artist = c.getString(artistC)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知歌手",
                    artistId = c.getLong(artistIdC),
                    album = c.getString(albumC)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知专辑",
                    albumId = c.getLong(albumIdC),
                    durationMs = c.getLong(durationC),
                    sizeBytes = c.getLong(sizeC),
                    dateModifiedMs = c.getLong(dateC) * 1000,
                    track = c.getInt(trackC) % 1000,
                    path = path,
                )
            }
        }
        return result
    }

    fun songById(id: Long): Song? = _songs.value.firstOrNull { it.id == id }

    fun songsByIds(ids: List<Long>): List<Song> =
        ids.mapNotNull { id -> _songs.value.firstOrNull { it.id == id } }

    fun uriFor(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    fun albumsOf(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (id, list) ->
                Album(id = id, name = list.first().album, artist = list.first().artist, songCount = list.size)
            }
            .sortedBy { it.name.lowercase() }

    fun artistsOf(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artistId }
            .map { (id, list) ->
                Artist(
                    id = id,
                    name = list.first().artist,
                    songCount = list.size,
                    albumCount = list.groupBy { it.albumId }.size,
                )
            }
            .sortedBy { it.name.lowercase() }

    fun songsOfAlbum(albumId: Long): List<Song> =
        _songs.value.filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.track }, { it.title.lowercase() }))

    fun songsOfArtist(artistId: Long): List<Song> =
        _songs.value.filter { it.artistId == artistId }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.track }, { it.title.lowercase() }))

    fun foldersOf(songs: List<Song>): List<Folder> =
        songs.groupBy { it.folder }
            .map { (path, list) ->
                Folder(path = path, name = path.substringAfterLast('/').ifBlank { "/" }, songCount = list.size)
            }
            .sortedBy { it.name.lowercase() }

    fun songsOfFolder(path: String): List<Song> =
        _songs.value.filter { it.folder == path }
            .sortedWith(compareBy { it.fileName.lowercase() })
}
