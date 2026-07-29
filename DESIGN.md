# Delayed Subtitle Learning Assistant — 技术方案（已确认）

> 目标：一个"延迟反馈字幕学习播放器"。保留原视频体验，英文正常显示，**中文延迟显示**，
> 强迫先主动理解英文、再得到中文答案。不是翻译软件，是英语听力训练器。

## 1. 核心设计决策

- **播放器不渲染字幕**：ExoPlayer 只播视频/音频，并禁用内置字幕轨（`C.TRACK_TYPE_TEXT`）。
  原因：若让播放器自己渲染字幕，就无法做到"同一条字幕里中文比英文晚出现"。
- **字幕走独立链路**：自己解析 SRT → 按播放位置驱动状态机 → Compose 覆盖层自绘。
- **状态机是播放位置的纯函数**：每 100ms 轮询 `player.currentPosition` 计算应显示内容，
  不依赖定时器。暂停 / 拖进度条 / 倍速全部天然正确。

## 2. 开源复用（不重复造轮子）

| 能力 | 方案 | 备注 |
|---|---|---|
| 视频播放 | **AndroidX Media3 ExoPlayer** (`media3-exoplayer`) | 支持 mp4 / mkv / HEVC 硬解 |
| Compose 视频表面 | `media3-ui-compose` (`PlayerSurface`) | |
| SRT 解析 | **Media3 自带 `SubripParser`** (`media3-extractor`) | 成熟方案；ASS 二期换 `SsaParser`，零架构改动 |
| 最近播放 | Room (`media3` 无关，标准 Jetpack) | |
| 设置持久化 | DataStore Preferences | 延迟上限 / 学习模式 |
| 文件导入 | SAF `OpenDocument` + 持久化 URI 权限 | 无需存储权限 |

> 用户原给的 `opusforandroid/subtitle-parser` 仓库不存在，已替换为 Media3 内置解析器；
> 备选第三方库：`avidraghav/SRTParser`。

## 3. 自适应延迟算法（Adaptive Subtitle Delay Engine）

```
duration = endTime - startTime
delay = max( min( duration * 0.5, MAX_DELAY ), MIN_DELAY )

默认: MAX_DELAY = 3000ms, MIN_DELAY = 1000ms
```

| 字幕时长 | 计算 | 结果 |
|---|---|---|
| 10s–12s (2s) | max(min(1000,3000),1000) | 1000ms |
| 10s–16s (6s) | max(min(3000,3000),1000) | 3000ms |
| 10s–30s (20s) | max(min(10000,3000),1000) | 3000ms |

**边界处理**：当 `duration ≤ 2s` 时公式会算出 `delay ≥ duration`（中文永远不显示），
此时回退为 `delay = duration / 2`（中文显示后半段）。

## 4. 字幕状态机

```
ENGLISH_VISIBLE   : 英文显示、中文隐藏（学习模式刚开始）
CHINESE_VISIBLE   : 英文 + 中文都显示
NONE              : 当前无字幕
```

- 普通模式：英文、中文在字幕开始即同时显示。
- 学习模式：英文在开始时显示；中文在 `startTime + delay` 后显示。

## 5. 项目结构

```
app
├── data
│   ├── subtitle          # SubtitleRepository（从 URI 读 SRT → 模型）
│   ├── database          # Room：VideoEntity / Dao / Repository（最近播放）
│   └── settings          # DataStore：延迟上限 / 学习模式
├── player
│   └── Media3Player       # ExoPlayer 封装（禁用内置字幕轨）
├── subtitle
│   ├── parser            # SrtSubtitleParser（Media3）+ BilingualCueSplitter
│   ├── model             # SubtitleItem
│   └── renderer          # SubtitleRenderer 状态机
├── learning
│   └── DelayEngine        # 自适应延迟算法
└── ui                    # MainActivity / Home / Player / ViewModel / theme
```

内部数据结构 `SubtitleItem { startTime, endTime, englishText, chineseText }`（单位 ms）。

## 6. 已确认事项（来自用户）

1. 字幕素材：用户已有行尸走肉 S04E01 的 mkv（HEVC 1080p + 内嵌英/中字幕轨）。
   已用 ffmpeg 抽出英轨 + YYeTs 简中轨，按时间重叠合并为双语 SRT
   `TWD.S04E01.bilingual.srt`（439 条，97% 配对成功）。
2. 输入格式放宽：mp4 / mkv 均可，架构零改动。
3. 极短字幕回退 `duration/2`。
4. 包名 `com.huqi.delayedsub`；工程位于 `DelayedSubPlayer/`。

## 7. 开发提交计划（每功能一提交）

1. `docs:` 技术方案
2. `feat:` 工程骨架（Gradle / Manifest / 主题 / 依赖容器）
3. `feat:` SRT 解析 + 双语拆分
4. `feat:` 延迟引擎 + 字幕状态机
5. `feat:` Media3 播放器 + PlayerViewModel
6. `feat:` Room 最近播放 + DataStore 设置 + HomeViewModel
7. `feat:` 首页 + 播放页 UI（自定义延迟中文字幕覆盖层）
8. `test:` 单元测试用例

## 8. 暂不做（第二阶段）

AI 翻译 / OCR / Whisper / 自动生成字幕 / 在线解析 / 登录 / 云端 / 社交 / ASS 解析（预留接口）。
