package com.huqi.delayedsub.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.data.database.VideoEntity
import com.huqi.delayedsub.data.subtitle.SubtitleRepository
import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.player.Media3Player
import com.huqi.delayedsub.subtitle.SubtitleExtractor
import com.huqi.delayedsub.subtitle.SubtitleSource
import com.huqi.delayedsub.subtitle.SubtitleStream
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import com.huqi.delayedsub.subtitle.parser.SrtSubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 播放页 ViewModel。
 *
 * 字幕策略（v2，稳定版）：
 * 不再依赖 ExoPlayer 的 onCues 实时抓取内嵌字幕——它对「流式 / 网络链接」视频常常
 * 识别不到字幕轨，导致白屏。改为：
 *   1. 用 ffmpeg-kit 探测视频的字幕流（[SubtitleExtractor.probe]）；
 *   2. 自动或手动选择一条文本字幕流，抽取为本地 SRT（[SubtitleExtractor.extract]）；
 *   3. 用既有 SRT 解析 + 双语拆分 + 延迟覆盖层渲染。
 * 这样无论是本地文件还是网络链接，字幕都能稳定显示。
 *
 * 字幕来源类型（[SubtitleSource]）：
 * - EXTERNAL：用户单独提供的 .srt；
 * - EMBEDDED：从视频内嵌轨抽取得到（首选，自动选中文文本轨）；
 * - NONE：关闭。
 */
class PlayerViewModel(app: Application, private val videoId: Long) : AndroidViewModel(app) {

    private val container = (app as DelayedSubApplication).container
    private val player: ExoPlayer = Media3Player.create(app)

    private val _subtitles = MutableStateFlow<List<SubtitleItem>>(emptyList())
    val subtitles: StateFlow<List<SubtitleItem>> = _subtitles.asStateFlow()

    private val _video = MutableStateFlow<VideoEntity?>(null)
    val video: StateFlow<VideoEntity?> = _video.asStateFlow()

    private val _subtitleSource = MutableStateFlow(SubtitleSource.NONE)
    val subtitleSource: StateFlow<SubtitleSource> = _subtitleSource.asStateFlow()

    private val _subtitleStreams = MutableStateFlow<List<SubtitleStream>>(emptyList())
    val subtitleStreams: StateFlow<List<SubtitleStream>> = _subtitleStreams.asStateFlow()

    private val _selectedStream = MutableStateFlow<SubtitleStream?>(null)
    val selectedStream: StateFlow<SubtitleStream?> = _selectedStream.asStateFlow()

    private var _userChoseSource = false

    val maxDelayMs: StateFlow<Long> = container.settingsRepository.maxDelayMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), DelayEngine.MAX_DELAY_DEFAULT_MS)

    val learningMode: StateFlow<Boolean> = container.settingsRepository.learningMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), true)

    init {
        viewModelScope.launch {
            val v = container.videoRepository.get(videoId)
            _video.value = v
            v?.let {
                // 播放器仅负责放视频；字幕由我们抽取后渲染，因此禁用播放器内置字幕轨
                Media3Player.prepare(player, Uri.parse(it.videoUri), it.lastPositionMs)
                if (!it.subtitleUri.isNullOrBlank()) {
                    loadExternalSrt(Uri.parse(it.subtitleUri))
                } else {
                    probeAndAutoSelect(it.videoUri)
                }
            }
        }
    }

    private fun probeAndAutoSelect(videoUri: String) {
        viewModelScope.launch {
            val source = SubtitleExtractor.resolvePath(getApplication(), videoUri)
            val streams = withContext(Dispatchers.IO) { SubtitleExtractor.probe(source) }
            _subtitleStreams.value = streams
            if (streams.isEmpty() || _userChoseSource) return@launch
            val pick = pickDefaultStream(streams)
            if (pick != null) selectSubtitleStream(pick)
        }
    }

    /** 选择某条字幕流（抽取 + 解析 + 渲染）。 */
    fun selectSubtitleStream(stream: SubtitleStream) {
        _userChoseSource = true
        _selectedStream.value = stream
        _subtitleSource.value = SubtitleSource.EMBEDDED
        _subtitles.value = emptyList()
        viewModelScope.launch {
            val videoUri = _video.value?.videoUri ?: return@launch
            val source = SubtitleExtractor.resolvePath(getApplication(), videoUri)
            val out = File(getApplication<DelayedSubApplication>().cacheDir, "sub_${stream.index}.srt")
            val ok = withContext(Dispatchers.IO) { SubtitleExtractor.extract(source, stream.index, out) }
            if (ok) {
                val bytes = withContext(Dispatchers.IO) { out.readBytes() }
                _subtitles.value = SrtSubtitleParser.parse(bytes)
            }
        }
    }

    /** 在已探测的字幕流里自动挑选（优先中文文本轨，其次任意文本轨，图片轨兜底）。 */
    fun selectDefaultEmbeddedStream() {
        val streams = _subtitleStreams.value
        val pick = pickDefaultStream(streams) ?: return
        selectSubtitleStream(pick)
    }

    private fun pickDefaultStream(streams: List<SubtitleStream>): SubtitleStream? {
        streams.firstOrNull { isChinese(it.language) && !it.isBitmap }?.let { return it }
        streams.firstOrNull { !it.isBitmap }?.let { return it }
        return streams.firstOrNull()
    }

    private fun isChinese(lang: String?): Boolean {
        if (lang.isNullOrBlank()) return false
        val l = lang.lowercase()
        return l.startsWith("zh") || l.startsWith("chi") || l.contains("chinese")
    }

    fun selectExternalSource() {
        _userChoseSource = true
        _subtitleSource.value = SubtitleSource.EXTERNAL
        _selectedStream.value = null
        _subtitles.value = emptyList()
        val uri = _video.value?.subtitleUri
        if (!uri.isNullOrBlank()) loadExternalSrt(Uri.parse(uri))
    }

    private fun loadExternalSrt(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.subtitleRepository.load(getApplication(), uri) }
                .onSuccess {
                    _subtitleSource.value = SubtitleSource.EXTERNAL
                    _subtitles.value = it
                }
        }
    }

    fun selectNoSubtitle() {
        _userChoseSource = true
        _subtitleSource.value = SubtitleSource.NONE
        _selectedStream.value = null
        _subtitles.value = emptyList()
    }

    fun setLearningMode(on: Boolean) =
        viewModelScope.launch { container.settingsRepository.setLearningMode(on) }

    fun setMaxDelay(ms: Long) =
        viewModelScope.launch { container.settingsRepository.setMaxDelayMs(ms) }

    val exoPlayer: ExoPlayer get() = player

    override fun onCleared() {
        val pos = player.currentPosition
        viewModelScope.launch {
            _video.value?.let { container.videoRepository.touch(it.id, pos) }
        }
        player.release()
    }

    companion object {
        fun Factory(app: Application, videoId: Long) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlayerViewModel(app, videoId) as T
        }
    }
}
