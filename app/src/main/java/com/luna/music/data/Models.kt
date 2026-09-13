package com.luna.music.data

import android.net.Uri
import kotlinx.serialization.Serializable

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
    val track: Int,
    val path: String,
) {
    val artworkUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")

    val fileName: String
        get() = path.substringAfterLast('/')
}

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
) {
    val artworkUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$id")
}

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val sortMode: SortMode = SortMode.TITLE,
    val sortAsc: Boolean = true,
)

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class SortMode(val label: String) {
    TITLE("按标题"),
    ARTIST("按歌手"),
    ALBUM("按专辑"),
    DURATION("按时长"),
    DATE("按修改时间"),
}

fun sortSongs(songs: List<Song>, mode: SortMode, asc: Boolean): List<Song> {
    val comparator: Comparator<Song> = when (mode) {
        SortMode.TITLE -> compareBy { it.title.lowercase() }
        SortMode.ARTIST -> compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
        SortMode.ALBUM -> compareBy({ it.album.lowercase() }, { it.track }, { it.title.lowercase() })
        SortMode.DURATION -> compareBy { it.durationMs }
        SortMode.DATE -> compareBy { it.dateModifiedMs }
    }
    return if (asc) songs.sortedWith(comparator) else songs.sortedWith(comparator.reversed())
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return "%.1f %s".format(v, units[i])
}
