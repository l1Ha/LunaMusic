package com.luna.music.lyrics

import com.luna.music.data.Song
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class LyricsLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricsLine>, val synced: Boolean)

object LyricsParser {

    private val tagRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): Lyrics? {
        val collected = mutableListOf<LyricsLine>()
        raw.lineSequence().forEach { line ->
            val stamps = tagRegex.findAll(line).toList()
            if (stamps.isEmpty()) {
                val text = line.trim()
                if (text.isNotEmpty() && !text.startsWith("[")) {
                    collected += LyricsLine(-1L, text)
                }
            } else {
                val text = line.substringAfterLast(']').trim()
                stamps.forEach { m ->
                    val minutes = m.groupValues[1].toLong()
                    val seconds = m.groupValues[2].toLong()
                    val frac = when (val f = m.groupValues[3]) {
                        "" -> 0L
                        else -> f.take(3).padEnd(3, '0').toLong()
                    }
                    collected += LyricsLine(minutes * 60_000 + seconds * 1000 + frac, text)
                }
            }
        }
        val timed = collected.filter { it.timeMs >= 0 && it.text.isNotEmpty() }.sortedBy { it.timeMs }
        return when {
            timed.size >= 2 -> Lyrics(timed, synced = true)
            collected.any { it.text.isNotBlank() } ->
                Lyrics(collected.filter { it.text.isNotBlank() }.distinctBy { it.text }, synced = false)
            else -> null
        }
    }
}

object LyricsLoader {

    fun load(song: Song): Lyrics? {
        parseEmbedded(song)?.let { return it }
        parseSidecarFile(song)?.let { return it }
        return null
    }

    private fun parseSidecarFile(song: Song): Lyrics? {
        val dir = song.path.substringBeforeLast('/')
        val base = song.fileName.substringBeforeLast('.')
        val candidates = listOf("$base.lrc", "$base.txt", "${song.title}.lrc")
        for (name in candidates) {
            val file = File(dir, name)
            if (file.isFile) {
                val content = readTextSmart(file) ?: continue
                LyricsParser.parse(content)?.let { return it }
            }
        }
        return null
    }

    private fun parseEmbedded(song: Song): Lyrics? {
        val file = File(song.path)
        if (!file.isFile) return null
        val raw: String? = when (file.extension.lowercase()) {
            "mp3" -> parseId3Lyrics(file)
            "flac" -> parseFlacLyrics(file)
            else -> null
        }
        return raw?.let { LyricsParser.parse(it) }
    }

    // ---------- MP3: ID3v2 USLT / SYLT ----------

    private fun parseId3Lyrics(file: File): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                if (raf.read(header) != 10) return null
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
                val major = header[3].toInt() and 0xFF
                if (major < 3 || major > 4) return null
                val tagFlags = header[5].toInt()
                var tagSize = syncsafe(header, 6)
                if (tagSize <= 0) return null

                val data = ByteArray(minOf(tagSize, 8_000_000))
                val read = raf.read(data)
                if (read <= 0) return null
                val buf = data.copyOf(read)

                var pos = 0
                if (tagFlags and 0x40 != 0) {
                    // 跳过扩展头
                    if (pos + 4 > buf.size) return null
                    val extSize = if (major == 4) syncsafe(buf, pos) else fourBytes(buf, pos)
                    pos += if (major == 4) extSize else extSize + 4
                }

                while (pos + 10 <= buf.size) {
                    val id = String(buf, pos, 4, Charsets.ISO_8859_1)
                    if (!id.matches(Regex("[A-Z0-9]{4}"))) break
                    val frameSize = if (major == 4) syncsafe(buf, pos + 4) else fourBytes(buf, pos + 4)
                    if (frameSize <= 0 || pos + 10 + frameSize > buf.size) break
                    val frameData = buf.copyOfRange(pos + 10, pos + 10 + frameSize)
                    when (id) {
                        "USLT" -> decodeUslt(frameData)?.let { return it }
                        "SYLT" -> decodeSylt(frameData)?.let { return it }
                    }
                    pos += 10 + frameSize
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt()
        var offset = 4
        val (descriptor, next) = readTerminated(data, offset, encoding)
        offset = next
        if (offset >= data.size) return null
        val text = decodeString(data, offset, encoding)
        return text?.takeIf { it.isNotBlank() }
    }

    private fun decodeSylt(data: ByteArray): String? {
        if (data.size < 6) return null
        val encoding = data[0].toInt()
        var offset = 6
        val (_, next) = readTerminated(data, offset, encoding)
        offset = next
        val parts = mutableListOf<LyricsLine>()
        while (offset < data.size) {
            val (line, afterText) = readTerminated(data, offset, encoding)
            offset = afterText
            if (offset + 4 > data.size) break
            val timeMs = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
            offset += 4
            if (line.isNotBlank()) {
                parts += LyricsLine(timeMs, line.trim())
            }
        }
        if (parts.size < 2) return null
        // 转成 LRC 文本，交给统一解析器
        val lrc = parts.joinToString("\n") { line ->
            val totalSec = line.timeMs / 1000.0
            val mm = totalSec.toInt() / 60
            val ss = totalSec - mm * 60
            "[%02d:%05.2f]%s".format(mm, ss, line.text)
        }
        return lrc
    }

    private fun readTerminated(data: ByteArray, start: Int, encoding: Int): Pair<String, Int> {
        if (encoding == 1 || encoding == 2) {
            var i = start
            while (i + 1 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                    val text = decodeString(data, start, encoding, i - start) ?: ""
                    return text to i + 2
                }
                i += 2
            }
            return "" to data.size
        } else {
            var i = start
            while (i < data.size && data[i] != 0.toByte()) i++
            val text = decodeString(data, start, encoding, i - start) ?: ""
            return text to (i + 1).coerceAtMost(data.size)
        }
    }

    private fun decodeString(data: ByteArray, offset: Int, encoding: Int, length: Int = data.size - offset): String? {
        if (offset >= data.size || length <= 0) return null
        val end = (offset + length).coerceAtMost(data.size)
        val slice = data.copyOfRange(offset, end)
        return when (encoding) {
            1 -> {
                if (slice.size >= 2 && slice[0] == 0xFF.toByte() && slice[1] == 0xFE.toByte()) {
                    String(slice, 2, slice.size - 2, Charsets.UTF_16LE)
                } else if (slice.size >= 2 && slice[0] == 0xFE.toByte() && slice[1] == 0xFF.toByte()) {
                    String(slice, 2, slice.size - 2, Charsets.UTF_16BE)
                } else {
                    String(slice, Charsets.UTF_16LE)
                }
            }
            2 -> String(slice, Charsets.UTF_16BE)
            3 -> String(slice, Charsets.UTF_8)
            else -> String(slice, Charsets.ISO_8859_1)
        }
    }

    private fun syncsafe(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)

    private fun fourBytes(b: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(b, offset, 4).order(ByteOrder.BIG_ENDIAN).int

    // ---------- FLAC: VORBIS_COMMENT ----------

    private fun parseFlacLyrics(file: File): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                if (raf.read(magic) != 4 || String(magic, Charsets.ISO_8859_1) != "fLaC") return null
                while (true) {
                    val header = ByteArray(4)
                    if (raf.read(header) != 4) return null
                    val isLast = header[0].toInt() and 0x80 != 0
                    val type = header[0].toInt() and 0x7F
                    val size = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                    if (size <= 0 || size > 16_000_000) return null
                    if (type == 4) {
                        val block = ByteArray(size)
                        if (raf.read(block) != size) return null
                        val lyrics = vorbisCommentLyrics(block)
                        if (lyrics != null) return lyrics
                    } else {
                        raf.seek(raf.filePointer + size)
                    }
                    if (isLast) return null
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun vorbisCommentLyrics(block: ByteArray): String? {
        val buf = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = buf.int
        if (vendorLen < 0 || vendorLen > buf.remaining()) return null
        buf.position(buf.position() + vendorLen)
        val count = buf.int
        var found: String? = null
        repeat(count.coerceAtMost(1000)) {
            if (found != null || buf.remaining() < 4) return@repeat
            val len = buf.int
            if (len <= 0 || len > buf.remaining()) return@repeat
            val bytes = ByteArray(len)
            buf.get(bytes)
            val entry = String(bytes, Charsets.UTF_8)
            val eq = entry.indexOf('=')
            if (eq > 0) {
                val key = entry.substring(0, eq).uppercase()
                val value = entry.substring(eq + 1)
                if ((key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "LYRICS-CHT" || key == "LYRICS-CHS") && value.isNotBlank()) {
                    found = value
                }
            }
        }
        return found
    }

    private fun readTextSmart(file: File): String? {
        return try {
            val bytes = file.readBytes()
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains('\uFFFD')) {
                runCatching { String(bytes, Charset.forName("GBK")) }.getOrDefault(utf8)
            } else {
                utf8
            }
        } catch (_: Exception) {
            null
        }
    }
}
