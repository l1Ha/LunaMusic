package com.luna.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * App 内更新：从 GitHub Releases 检查最新版本、下载 APK 并唤起安装。
 */
object UpdateManager {

    const val REPO = "l1Ha/LunaMusic"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    /** lite 渠道包名不同（com.luna.music.lite），无法用完整版 APK 覆盖升级 */
    val isLiteBuild: Boolean =
        com.luna.music.BuildConfig.APPLICATION_ID.endsWith(".lite")

    data class Release(
        val version: String,
        val name: String,
        val downloadUrl: String,
        val size: Long,
    )

    sealed interface UpdateStatus {
        data object Idle : UpdateStatus
        data object Checking : UpdateStatus
        data object UpToDate : UpdateStatus
        data class Available(val release: Release) : UpdateStatus
        data object Downloading : UpdateStatus
        data class Ready(val file: File) : UpdateStatus
        data class Error(val message: String) : UpdateStatus
    }

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val version = tag.removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            val apk = selectApkAsset(assets, version) ?: return@runCatching null
            Release(
                version = version,
                name = json.optString("name").ifBlank { tag },
                downloadUrl = apk.optString("browser_download_url"),
                size = apk.optLong("size"),
            )
        }.getOrNull()
    }

    /**
     * 从 Release 资产中挑选完整版 APK。
     * GitHub API 返回的资产顺序不保证与上传顺序一致（实测 lite 常常排在前面），
     * 因此绝不能"取第一个 .apk"。优先级：
     * 1. 精确匹配 LunaMusic-v<version>.apk / LunaMusic-<version>.apk
     * 2. 任意非 lite 的 .apk
     * 3. 兜底取任意 .apk
     */
    internal fun selectApkAsset(assets: org.json.JSONArray, version: String): JSONObject? {
        val exactNames = setOf("LunaMusic-v$version.apk", "LunaMusic-$version.apk")
        var fallback: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (!name.endsWith(".apk")) continue
            if (fallback == null) fallback = a
            if (name in exactNames) return a
        }
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk") && !name.contains("lite", ignoreCase = true)) return a
        }
        return fallback
    }

    suspend fun download(context: Context, release: Release): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val final = File(dir, "LunaMusic-${release.version}.apk")
            val part = File(dir, final.name + ".part")
            val conn = URL(release.downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.inputStream.use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            // 完整性校验：非空 + PK 魔数 + 与 Release 资产大小一致，
            // 避免截断/损坏的 APK 被送去安装（系统会报“软件包似乎无效”）
            val ok = part.length() > 0 &&
                (release.size <= 0 || part.length() == release.size) &&
                part.inputStream().use { s ->
                    val head = ByteArray(2)
                    s.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
                }
            if (ok) {
                final.delete()
                part.renameTo(final)
                final
            } else {
                part.delete()
                null
            }
        }.getOrNull()
    }

    fun installApk(context: Context, file: File): Boolean {
        // Android 8+ 需要用户先授予“安装未知应用”权限，否则安装界面静默失败
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 语义化版本比较：a > b 返回正数。 */
    fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
