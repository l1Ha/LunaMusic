# LunaMusic 🌙🎵

一款纯粹、好用的 **Android 本地音乐播放器**。基于 **Kotlin + Jetpack Compose + Media3** 构建，Material 3 设计，完全离线运行，不上传任何数据。

> 设计参考了 Musicolet、Poweramp、Retro Music、Salt Player 等优秀播放器的功能体验，只做本地播放这一件事，并把它做好。

## ✨ 功能特性

### 音乐库
- 📚 自动扫描本地音频（MediaStore，自动过滤 30 秒以下的短音频/录音）
- 📁 **指定扫描文件夹**：可手动选择 / 快捷添加要扫描的目录（多选、移除、恢复默认），只看自己想要的音乐
- 🎼 按 **歌曲 / 专辑 / 歌手 / 文件夹 / 播放列表** 五个维度浏览
- 🔍 全局搜索：歌曲、专辑、歌手统一搜索
- ↕️ 多种排序方式（标题 / 歌手 / 专辑 / 时长 / 修改时间，支持升降序）

### 播放体验
- ▶️ 基于 Media3 (ExoPlayer) 的稳定播放，自动处理音频焦点（来电暂停、拔耳机暂停）
- 💾 **断点续播**：退出后重新打开自动恢复上次队列与进度，倍速也一并记忆
- 🅆 **WMA / APE 支持**：内嵌 FFmpeg，本地转码为 AAC (256k) 后无缝播放（首次点播数秒，之后秒播）；设置页可一键清理转码缓存
- 👆 **封面左右滑动切歌**：播放页横滑即切换上一首/下一首，标题超长自动跑马灯滚动
- 🎛 **五段均衡器 + 预设 + 低音增强 + 环绕声**（实时生效）
- 📝 **歌词显示**：支持内嵌歌词（ID3v2 USLT/SYLT、FLAC）与同名 `.lrc` 文件（自动识别 UTF-8 / GBK），逐行高亮、自动滚动、点击行跳转
- 🔀 随机播放 / 单曲循环 / 列表循环
- ⏱ **睡眠定时器**（10-90 分钟 / 播完当前歌曲后停止）
- ⚡ **倍速播放**（0.5x - 2.0x）
- ➕ **下一首播放**：长按歌曲插队到当前曲目之后
- ❤️ 收藏 + 自定义播放列表
- 📋 播放队列查看与跳转、清空

### 系统集成
- 🔔 通知栏 & 锁屏播放控制（MediaSession）
- 🎧 蓝牙 / 有线耳机线控
- 🔄 **App 内检查更新**：一键下载安装 GitHub 最新 Release
- 🎨 Material You 动态取色（Android 12+），深色 / 浅色 / 跟随系统
- 🌓 边到边全面屏适配，Android 15 (API 35) 目标版本

## 🎵 格式支持

- **原生支持**：MP3、FLAC、M4A/AAC、OGG、WAV、OPUS、MIDI 等系统解码器支持的格式
- **WMA / APE / WavPack**：LunaMusic 内嵌 FFmpeg，播放时在本地把这类文件**转码为 AAC 并缓存**，首次点播有几秒钟转换时间，之后直接秒播；转码文件保存在应用缓存目录，可随时清理

## 📱 系统要求

Android 8.0（API 26）及以上。当前 APK 仅打包 **arm64-v8a**（覆盖几乎所有近年手机）；如需 x86_64 模拟器或老款 32 位设备，可自行在 `app/build.gradle.kts` 调整 `abiFilters` 后重新构建。

## 🚀 下载安装

前往 [Releases](https://github.com/l1Ha/LunaMusic/releases)，按需下载：

| 文件 | 说明 |
| --- | --- |
| `LunaMusic-<版本>.apk` | **完整版**（含 FFmpeg，支持 WMA / APE），包名 `com.luna.music` |
| `LunaMusic-<版本>-lite.apk` | 轻量版（无 FFmpeg，不支持 WMA / APE），包名 `com.luna.music.lite`，可与完整版共存 |

- 安装时如提示"未知来源"，按提示允许浏览器/文件管理器安装应用即可
- 若安装失败，先核对下载文件大小是否与 Release 页标注一致（网络中断导致的截断文件是最常见原因）；仍失败可用 `adb install -r xxx.apk` 查看具体错误码
- 安装前请授予音频访问权限

## 🛠 构建方法

```bash
git clone https://github.com/l1Ha/LunaMusic.git
cd LunaMusic
./gradlew assembleFullDebug          # 完整版
./gradlew assembleLiteDebug          # 轻量版（无 FFmpeg）
# 产物: app/build/outputs/apk/{full,lite}/debug/*-debug.apk
# 正式版: ./gradlew assembleFullRelease assembleLiteRelease（需签名配置）
```

要求：JDK 17+、Android SDK（compileSdk 35）。Android Studio 直接打开工程即可。

## 🧱 技术栈

| 组件 | 说明 |
| --- | --- |
| Kotlin 2.0 | 全部代码为 Kotlin |
| Jetpack Compose | Material 3 声明式 UI |
| Media3 / ExoPlayer | 播放引擎 + MediaSessionService |
| DataStore | 设置、收藏、播放列表、播放状态持久化 |
| kotlinx.serialization | 播放列表 JSON 序列化 |
| Coil | 封面图片加载 |
| androidx.media.audiofx | 均衡器 / 低音增强 / 环绕声 |
| JavaCV (FFmpeg 6.1) | WMA / APE 本地转码为 AAC（仅 full 渠道携带） |

## 📂 目录结构

```
app/src/main/java/com/luna/music/
├── MainActivity.kt          # 入口 Activity
├── MainViewModel.kt         # 应用状态（设置/收藏/播放列表）
├── data/                    # MediaStore 扫描、模型、DataStore 持久化
├── playback/                # 播放服务、MediaController 桥接、音效
├── lyrics/                  # 歌词解析（LRC / 内嵌）
└── ui/                      # Compose 界面（库/播放页/均衡器/设置…）
```

## 🗺 Roadmap

- [x] 播放页滑动封面切换歌曲
- [ ] 桌面小组件
- [ ] 歌词翻译行、卡拉OK式逐字歌词
- [ ] 多语言（English）

## 📄 许可证

[MIT](LICENSE)
