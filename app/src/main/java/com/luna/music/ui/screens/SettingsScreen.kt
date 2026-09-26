package com.luna.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luna.music.BuildConfig
import com.luna.music.MainViewModel
import com.luna.music.data.ThemeMode
import com.luna.music.data.UpdateManager
import com.luna.music.playback.TranscodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenScanFolders: () -> Unit,
    onOpenPlaybackLog: () -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val songCount by vm.songs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<UpdateManager.UpdateStatus>(UpdateManager.UpdateStatus.Idle) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("外观")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.updateSettings { it.copy(themeMode = mode) } }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                    )
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "跟随壁纸取色（Material You）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { checked ->
                            vm.updateSettings { s -> s.copy(dynamicColor = checked) }
                        },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("音乐库")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已索引 ${songCount.size} 首歌曲",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (isScanning) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = { vm.rescan() }) { Text("重新扫描") }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenScanFolders)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("扫描文件夹", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        buildString {
                            append(
                                if (settings.scanFolders.isEmpty()) "扫描全部文件夹"
                                else "已指定 ${settings.scanFolders.size} 个文件夹",
                            )
                            if (settings.excludedFolders.isNotEmpty()) append("，已排除 ${settings.excludedFolders.size} 个")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // WMA/APE 转码缓存（仅 full 渠道会写入，lite 为空不显示）
            var transcodedBytes by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(Unit) {
                transcodedBytes = withContext(Dispatchers.IO) {
                    TranscodeManager.cacheDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
            }
            val cacheBytes = transcodedBytes
            if (cacheBytes != null && cacheBytes > 0L) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("清理转码缓存", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "已用 ${formatCacheSize(cacheBytes)}（WMA/APE 播放用，清理后首次点播需重新转换）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            transcodedBytes = withContext(Dispatchers.IO) {
                                TranscodeManager.cacheDir(context).listFiles()?.forEach { it.delete() }
                                0L
                            }
                        }
                    }) { Text("清理") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("播放")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("焦点恢复后自动续播", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "被其他应用长时间占用音频焦点时，对方释放后自动继续播放（来电等短暂打断由系统自动恢复）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.autoResumeAfterFocusLoss,
                    onCheckedChange = { checked ->
                        vm.updateSettings { s -> s.copy(autoResumeAfterFocusLoss = checked) }
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("诊断")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPlaybackLog)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("播放日志", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "记录暂停/恢复的原因与播放错误，便于定位播放问题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("更新")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("当前版本 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    updateText(updateStatus)
                }
                OutlinedButton(
                    onClick = {
                        if (UpdateManager.isLiteBuild) {
                            updateStatus = UpdateManager.UpdateStatus.Error(
                                "轻量版不支持应用内更新，请在 GitHub Releases 下载完整版（两者可共存）",
                            )
                            return@OutlinedButton
                        }
                        updateStatus = UpdateManager.UpdateStatus.Checking
                        scope.launch {
                            val release = UpdateManager.check()
                            updateStatus = when {
                                release == null -> UpdateManager.UpdateStatus.Error("检查失败，请检查网络后重试")
                                UpdateManager.compareVersion(release.version, BuildConfig.VERSION_NAME) > 0 ->
                                    UpdateManager.UpdateStatus.Available(release)
                                else -> UpdateManager.UpdateStatus.UpToDate
                            }
                        }
                    },
                ) { Text("检查更新") }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("关于")
            Text("LunaMusic v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.padding(4.dp))
            Text(
                "一款基于 Jetpack Compose + Media3 的本地音乐播放器。\n\n" +
                    "· 完全离线，不联网（仅在检查更新时访问 GitHub）、不上传任何数据\n" +
                    "· WMA / APE 支持：本地 FFmpeg 转码为 AAC 后播放（首次点播需数秒）\n" +
                    "· 支持内嵌歌词与同名 .lrc 歌词\n" +
                    "· 通知栏 / 锁屏控制、蓝牙耳机线控",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(8.dp))
            Text(
                "源代码：github.com/l1Ha/LunaMusic",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/l1Ha/LunaMusic")),
                    )
                },
            )
        }
    }

    (updateStatus as? UpdateManager.UpdateStatus.Available)?.let { available ->
        AlertDialog(
            onDismissRequest = { updateStatus = UpdateManager.UpdateStatus.Idle },
            title = { Text("发现新版本") },
            text = {
                Text("最新版本 v${available.release.version}\n当前版本 v${BuildConfig.VERSION_NAME}\n\n是否下载并安装？")
            },
            confirmButton = {
                TextButton(onClick = {
                    updateStatus = UpdateManager.UpdateStatus.Downloading
                    scope.launch {
                        val file = UpdateManager.download(context, available.release)
                        if (file != null && UpdateManager.installApk(context, file)) {
                            updateStatus = UpdateManager.UpdateStatus.Idle
                        } else {
                            updateStatus = UpdateManager.UpdateStatus.Error("下载或安装失败，请重试")
                        }
                    }
                }) { Text("下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateStatus = UpdateManager.UpdateStatus.Idle }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun updateText(status: UpdateManager.UpdateStatus) {
    val text = when (status) {
        UpdateManager.UpdateStatus.Idle -> ""
        UpdateManager.UpdateStatus.Checking -> "正在检查…"
        UpdateManager.UpdateStatus.UpToDate -> "已是最新版本"
        UpdateManager.UpdateStatus.Downloading -> "正在下载…"
        is UpdateManager.UpdateStatus.Error -> status.message
        is UpdateManager.UpdateStatus.Available -> ""
        is UpdateManager.UpdateStatus.Ready -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%d KB".format(bytes / (1L shl 10))
    else -> "$bytes B"
}