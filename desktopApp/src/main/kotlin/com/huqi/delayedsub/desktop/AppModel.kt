package com.huqi.delayedsub.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huqi.delayedsub.subtitle.SubtitleStream
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import com.huqi.delayedsub.subtitle.parser.SrtSubtitleParser
import java.awt.Component
import java.io.File

/**
 * 桌面版应用状态（与 Android 的 PlayerViewModel 等价，但用 Compose 的 State 持有，
 * 直接驱动 Compose UI）。字幕逻辑全部复用 shared 模块。
 */
class AppModel {
    var videoPath by mutableStateOf<String?>(null); private set
    var subtitles by mutableStateOf<List<SubtitleItem>>(emptyList()); private set
    var subtitleStreams by mutableStateOf<List<SubtitleStream>>(emptyList()); private set
    var selectedStream by mutableStateOf<SubtitleStream?>(null); private set
    var positionMs by mutableStateOf(0L); private set
    var durationMs by mutableStateOf(0L); private set
    var isPlaying by mutableStateOf(false); private set
    var learningMode by mutableStateOf(true); private set
    var maxDelayMs by mutableStateOf(3000L); private set
    var vlcError by mutableStateOf<String?>(null); private set
    var status by mutableStateOf("请打开一个视频文件，或粘贴网络链接（http/https）"); private set

    private var player: DesktopPlayer? = null
    private var userChose = false

    fun playerView(): Component? = player?.view

    fun loadVideo(path: String) {
        videoPath = path
        subtitles = emptyList()
        subtitleStreams = emptyList()
        selectedStream = null
        userChose = false
        status = "正在初始化播放器…"
        try {
            if (player == null) {
                player = DesktopPlayer(
                    onTime = { positionMs = it },
                    onDuration = { durationMs = it },
                    onEnd = { isPlaying = false },
                    onError = { vlcError = it }
                )
            }
            player?.load(path)
            isPlaying = true
            status = "正在探测字幕轨…"
        } catch (e: Throwable) {
            vlcError = "无法初始化播放器：请确认已安装 VLC 播放器。\n${e.message ?: e.toString()}"
            status = "播放器初始化失败"
            return
        }
        val streams = FfmpegExtractor.probe(path)
        subtitleStreams = streams
        status = if (streams.isEmpty()) "未探测到内嵌字幕轨（可手动选择外部 .srt）" else "探测到 ${streams.size} 条字幕轨"
        if (streams.isNotEmpty()) selectDefault()
    }

    fun selectDefault() {
        val pick = subtitleStreams.firstOrNull { isChinese(it.language) && !it.isBitmap }
            ?: subtitleStreams.firstOrNull { !it.isBitmap }
            ?: subtitleStreams.firstOrNull()
        pick?.let { selectStream(it) }
    }

    fun selectStream(s: SubtitleStream) {
        userChose = true
        selectedStream = s
        val path = videoPath ?: return
        if (s.isBitmap) {
            subtitles = emptyList()
            status = "图片字幕（PGS/SUP）：由 VLC 内置渲染，无法文本延迟"
            return
        }
        status = "正在抽取字幕轨 #${s.index}…"
        runCatching {
            val out = File.createTempFile("dsp_sub", ".srt")
            if (FfmpegExtractor.extract(path, s.index, out)) {
                subtitles = SrtSubtitleParser.parse(out.readBytes())
                status = "已加载 ${subtitles.size} 条字幕（英文即时 / 中文延迟）"
            } else {
                status = "抽取失败，请换一条字幕轨"
            }
        }.onFailure { status = "抽取异常：${it.message}" }
    }

    fun loadExternalSrt(path: String) {
        runCatching {
            subtitles = SrtSubtitleParser.parse(File(path).readBytes())
            selectedStream = null
            status = "已加载外部字幕 ${subtitles.size} 条"
        }.onFailure { status = "外部字幕加载失败：${it.message}" }
    }

    fun playPause() {
        val p = player ?: return
        if (isPlaying) {
            p.pause()
            isPlaying = false
        } else {
            p.play()
            isPlaying = true
        }
    }

    fun seek(ms: Long) {
        player?.seek(ms)
        positionMs = ms
    }

    fun setLearning(on: Boolean) {
        learningMode = on
    }

    fun setMaxDelay(ms: Long) {
        maxDelayMs = ms
    }

    fun dispose() {
        player?.dispose()
        player = null
    }

    private fun isChinese(lang: String?): Boolean {
        if (lang.isNullOrBlank()) return false
        val l = lang.lowercase()
        return l.startsWith("zh") || l.startsWith("chi") || l.contains("chinese")
    }
}
