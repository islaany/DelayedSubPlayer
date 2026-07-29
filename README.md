# Delayed Subtitle Learning Assistant（延迟字幕学习器）

一个 Android 英语影视学习播放器：**英文正常显示，中文延迟显示**，
用"先理解、后答案"的方式训练英语听力。不是翻译软件。

## 技术栈

- 语言：Kotlin
- 架构：MVVM
- UI：Jetpack Compose
- 播放器：AndroidX Media3 ExoPlayer（不自己写播放器）
- 字幕解析：Media3 内置 `SubripParser`（不自己写解析器）
- 持久化：Room（最近播放）+ DataStore（设置）

## 核心特性

- 导入本地视频（mp4 / mkv）与对应 SRT 字幕
- 英文立即显示，中文按字幕时长自适应延迟出现（`delay = max(min(duration*0.5, 3000), 1000)`）
- 学习模式 / 普通模式切换
- 延迟上限可设 3s / 5s
- 最近学习列表（断点续播）

## 如何运行

1. 用 **Android Studio（Hedgehog 或更新）** 打开本目录（Gradle  wrapper 会在首次导入时自动补全）。
2. 连接设备或启动模拟器（API 26+）。
3. 点击 Run。

> 说明：本仓库不含二进制 `gradle-wrapper.jar`，首次在 Android Studio 导入会自动生成。
> 若需命令行构建，请先执行一次 Android Studio 导入，或使用本机已安装的 Gradle 8.9+。

## 目录结构

见 [DESIGN.md](DESIGN.md) 第 5 节。

## 测试

`./gradlew testDebugUnitTest` 运行纯逻辑单元测试（延迟引擎、双语拆分、状态机、SRT 解析）。
