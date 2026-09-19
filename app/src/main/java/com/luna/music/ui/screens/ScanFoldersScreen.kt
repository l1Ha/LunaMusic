package com.luna.music.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luna.music.MainViewModel
import com.luna.music.data.Folder
import com.luna.music.data.MediaRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanFoldersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val selected = settings.scanFolders
    val excluded = settings.excludedFolders
    var discovered by remember { mutableStateOf<List<Folder>>(emptyList()) }

    LaunchedEffect(Unit) {
        discovered = MediaRepository.discoverFolders(context, excluded)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val path = resolveTreePath(context, uri)
            if (path != null) {
                vm.addScanFolder(path)
                Toast.makeText(context, "已添加扫描文件夹：$path", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "无法识别该目录（仅支持内部存储 / SD 卡）", Toast.LENGTH_LONG).show()
            }
        }
    }

    val excludeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val path = resolveTreePath(context, uri)
            if (path != null) {
                vm.addExcludedFolder(path)
                Toast.makeText(context, "已排除：$path", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "无法识别该目录（仅支持内部存储 / SD 卡）", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 未选中、不是已选目录子目录、也未处于排除范围内的快捷候选
    val candidates = discovered.filter { folder ->
        selected.none { sel ->
            folder.path == sel || folder.path.startsWith(sel.trimEnd('/') + "/")
        } && excluded.none { ex ->
            folder.path == ex || folder.path.startsWith(ex.trimEnd('/') + "/")
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("扫描文件夹") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    buildString {
                        append(
                            if (selected.isEmpty()) "当前扫描：全部文件夹（默认）"
                            else "已指定 ${selected.size} 个文件夹，仅扫描其中的音乐",
                        )
                        if (excluded.isNotEmpty()) append("；已排除 ${excluded.size} 个文件夹")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("选择目录")
                    }
                    OutlinedButton(
                        onClick = { vm.clearScanFolders() },
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复默认")
                    }
                }
            }

            if (selected.isNotEmpty()) {
                item { Text("已指定", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(selected, key = { it }) { path ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/').ifBlank { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { vm.removeScanFolder(path) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Text(
                    "排除的文件夹",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "其中的音频不会扫描、也不会显示（优先于扫描范围）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                OutlinedButton(
                    onClick = { excludeLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.FolderOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("选择要排除的目录")
                }
            }
            items(excluded, key = { it }) { path ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(path.substringAfterLast('/').ifBlank { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.removeExcludedFolder(path) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "取消排除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                item {
                    Text(
                        "快捷添加（检测到的音乐文件夹）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(candidates, key = { it.path }) { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.addScanFolder(folder.path) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${folder.songCount} 首 · ${folder.path}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "添加",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 把 SAF 文档树 Uri 解析回文件系统绝对路径（仅支持外部存储提供商）。 */
private fun resolveTreePath(context: Context, treeUri: Uri): String? = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val colon = docId.indexOf(':')
    if (colon <= 0) return@runCatching null
    val volume = docId.substring(0, colon)
    val rel = Uri.decode(docId.substring(colon + 1))?.trimEnd('/')
    when {
        volume == "primary" -> {
            val base = "/storage/emulated/0"
            if (rel.isNullOrBlank()) "$base/" else "$base/$rel".trimEnd('/')
        }
        volume.startsWith("raw:") -> null
        else -> {
            val base = "/storage/$volume"
            if (rel.isNullOrBlank()) "$base/" else "$base/$rel".trimEnd('/')
        }
    }
}.getOrNull()