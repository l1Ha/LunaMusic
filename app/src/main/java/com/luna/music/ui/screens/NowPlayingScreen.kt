package com.luna.music.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.luna.music.MainViewModel
import com.luna.music.data.Song
import com.luna.music.data.formatDuration
import com.luna.music.lyrics.Lyrics
import com.luna.music.lyrics.LyricsLoader
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.components.CoverImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class NPSheet { SPEED, SLEEP, QUEUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    vm: MainViewModel,
    song: Song,
    positionMs: Long,
    durationMs: Long,
    onClose: () -> Unit,
    onOpenAlbum: () -> Unit,
) {
    val favorites by vm.favorites.collectAsState()
    val isPlaying by PlayerBridge.isPlaying.collectAsState()
    val shuffle by PlayerBridge.shuffleEnabled.collectAsState()
    val repeatMode by PlayerBridge.repeatMode.collectAsState()
    val queue by PlayerBridge.queue.collectAsState()
    val sleepRemaining by PlayerBridge.sleepRemainingMs.collectAsState()
    val stopAfterCurrent by PlayerBridge.stopAfterCurrent.collectAsState()
    val speed by PlayerBridge.speed.collectAsState()
    val nowIndex by PlayerBridge.currentIndex.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<NPSheet?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val lyrics by produceState<Lyrics?>(initialValue = null, key1 = song.id) {
        value = withContext(Dispatchers.IO) { LyricsLoader.load(song) }
    }

    val bg = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    Surface(color = bg) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), bg, bg))),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { sheet = NPSheet.QUEUE }) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "播放队列")
                    }
                }

                Crossfade(
                    targetState = showLyrics,
                    modifier = Modifier.weight(1f),
                    label = "nowPlaying",
                ) { lyr ->
                    if (!lyr) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.weight(0.7f))
                            CoverImage(
                                model = song.artworkUri,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .clickable { showLyrics = true },
                                shape = MaterialTheme.shapes.extraLarge,
                            )
                            Spacer(Modifier.weight(0.7f))

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable(onClick = onOpenAlbum),
                                    )
                                }
                                IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                                    Icon(
                                        imageVector = if (song.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "收藏",
                                        tint = if (song.id in favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            val maxMs = durationMs.coerceAtLeast(1L).toFloat()
                            Slider(
                                value = (if (dragging) dragPositionMs else positionMs.toFloat()).coerceIn(0f, maxMs),
                                onValueChange = {
                                    dragging = true
                                    dragPositionMs = it
                                },
                                onValueChangeFinished = {
                                    PlayerBridge.seekTo(dragPositionMs.toLong())
                                    dragging = false
                                },
                                valueRange = 0f..maxMs,
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    formatDuration(if (dragging) dragPositionMs.toLong() else positionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    formatDuration(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { PlayerBridge.setShuffleEnabled(!shuffle) }) {
                                    Icon(
                                        Icons.Rounded.Shuffle,
                                        contentDescription = "随机播放",
                                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { PlayerBridge.previous() }) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
                                }
                                FilledIconButton(
                                    onClick = { PlayerBridge.toggle() },
                                    modifier = Modifier.size(72.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "播放/暂停",
                                        modifier = Modifier.size(38.dp),
                                    )
                                }
                                IconButton(onClick = { PlayerBridge.next() }) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
                                }
                                IconButton(onClick = { PlayerBridge.cycleRepeatMode() }) {
                                    Icon(
                                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                        contentDescription = "循环模式",
                                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                }
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { showLyrics = true }) { Text("词") }
                                TextButton(onClick = { sheet = NPSheet.SPEED }) { Text("%.1fx".format(speed)) }
                                TextButton(onClick = { sheet = NPSheet.SLEEP }) {
                                    val sleepActive = sleepRemaining != null || stopAfterCurrent
                                    Icon(
                                        Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (sleepActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (sleepActive) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (sleepRemaining != null) {
                                                PlayerBridge.formatSleepRemaining(sleepRemaining ?: 0L)
                                            } else {
                                                "播完本曲"
                                            },
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            LyricsPane(
                                lyrics = lyrics,
                                positionMs = positionMs,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { showLyrics = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) { Text("收起歌词") }
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        NPSheet.SPEED -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            SheetTitle("播放速度")
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                SheetOptionRow(
                    text = "%.1fx".format(s) + if (s == 1.0f) "（正常）" else "",
                    selected = speed == s,
                ) {
                    PlayerBridge.setSpeed(s)
                    sheet = null
                }
            }
            SheetSpacer()
        }
        NPSheet.SLEEP -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            SheetTitle("睡眠定时")
            sleepRemaining?.let {
                Text(
                    "将在 ${PlayerBridge.formatSleepRemaining(it)} 后暂停播放",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            if (stopAfterCurrent) {
                Text(
                    "将在播完当前歌曲后暂停",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            listOf(10, 20, 30, 45, 60, 90).forEach { minutes ->
                SheetOptionRow(text = "$minutes 分钟") {
                    PlayerBridge.startSleepTimer(minutes * 60_000L)
                    sheet = null
                }
            }
            SheetOptionRow(text = "当前歌曲播完后停止", selected = stopAfterCurrent) {
                PlayerBridge.setStopAfterCurrent(true)
                sheet = null
            }
            if (sleepRemaining != null || stopAfterCurrent) {
                SheetOptionRow(text = "取消定时", selected = false) {
                    PlayerBridge.cancelSleepTimer()
                    PlayerBridge.setStopAfterCurrent(false)
                    sheet = null
                }
            }
            SheetSpacer()
        }
        NPSheet.QUEUE -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            SheetTitle("播放队列（${queue.size} 首）")
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                itemsIndexed(queue) { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlayerBridge.seekToIndex(index)
                                sheet = null
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (index == nowIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (queue.isNotEmpty()) {
                TextButton(
                    onClick = {
                        PlayerBridge.clearQueue()
                        sheet = null
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("清空队列") }
            }
            SheetSpacer()
        }
        null -> Unit
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SheetOptionRow(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SheetSpacer() {
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LyricsPane(
    lyrics: Lyrics?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌词\n\n可将同名 .lrc 文件放在音乐文件同目录，\n或在音频文件中内嵌歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    var scrollPauseUntil by remember { mutableLongStateOf(0L) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) scrollPauseUntil = System.currentTimeMillis() + 4000
    }
    val currentLine = if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= positionMs } else -1
    LaunchedEffect(currentLine) {
        if (currentLine >= 0 && System.currentTimeMillis() >= scrollPauseUntil) {
            runCatching {
                listState.animateScrollToItem(
                    currentLine,
                    -(listState.layoutInfo.viewportSize.height / 3).coerceAtLeast(0),
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(vertical = 48.dp),
    ) {
        if (!lyrics.synced) {
            items(lyrics.lines) { line ->
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(lyrics.lines) { index, line ->
                val active = index == currentLine
                Text(
                    line.text.ifEmpty { "···" },
                    style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PlayerBridge.seekTo(line.timeMs) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                )
            }
        }
    }
}
