package com.huqi.delayedsub.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.data.database.VideoEntity
import com.huqi.delayedsub.data.subtitle.SubtitleRepository
import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.player.Media3Player
import com.huqi.delayedsub.subtitle.SubtitleSource
import com.huqi.delayedsub.subtitle.SubtitleStream
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import com.huqi.delayedsub.subtitle.parser.BilingualCueSplitter
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
 * 字幕策略（v3，纯 ExoPlayer 抓取，零外部依赖）：
 * 不依赖 ffmpeg——ffmpeg-kit 的 Maven 仓库（maven.arthenica.com）已无法解析，
 * 且 ExoPlayer 的 onCues 对「本地 / 网络链接」视频都会触发，足够稳定。
 *
 * 做法：
 *   1. 监听 onTracksChanged 枚举视频的字幕轨（文本轨 + 图片轨），列出供手动选；
 *   2. 自动或手动选中一条文本轨后，ExoPlayer 通过 onCues(CueGroup) 实时吐出
 *      Cue（含绝对 start/end 时间 + 文本），我们按时间线累积成 SubtitleItem，
 *      用既有双语拆分 + 延迟覆盖层渲染（英文即时、中文延迟）；
 *   3. 图片字幕轨（PGS/SUP）无法以文本显示，交给播放器自带 SubtitleView 渲染。
 *
 * 因此无论本地文件还是网络链接，字幕都能稳定显示，且中文延迟逻辑一致。
 *
 * 字幕来源类型（[SubtitleSource]）：
 * - EXTERNAL：用户单独提供的 .srt；
 * - EMBEDDED：从视频内嵌轨由 ExoPlayer 抓取得到（首选，自动选中文文本轨）；
 * - NONE：关闭。
 */
@OptIn(UnstableApi::class)
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

    // 与 _subtitleStreams 的 index 一一对应的 Tracks.Group（仅文本/图片轨）
    private var textGroups: List<Tracks.Group> = emptyList()

    val maxDelayMs: StateFlow<Long> = container.settingsRepository.maxDelayMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), DelayEngine.MAX_DELAY_DEFAULT_MS)

    val learningMode: StateFlow<Boolean> = container.settingsRepository.learningMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), true)

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            rebuildStreams(tracks)
        }

        override fun onCues(cueGroup: CueGroup) {
            ingestCues(cueGroup)
        }
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            val v = container.videoRepository.get(videoId)
            _video.value = v
            v?.let {
                // 播放器负责放视频；内嵌字幕由 onCues 抓取后渲染
                Media3Player.prepare(player, Uri.parse(it.videoUri), it.lastPositionMs)
                if (!it.subtitleUri.isNullOrBlank()) {
                    loadExternalSrt(Uri.parse(it.subtitleUri))
                }
                // 内嵌字幕在 onTracksChanged 触发后自动选轨，无需 ffmpeg
            }
        }
    }

    private fun rebuildStreams(tracks: Tracks) {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        textGroups = groups
        val streams = groups.mapIndexed { i, g ->
            val f = g.mediaTrackGroup.getFormat(0)
            val mime = f.sampleMimeType ?: ""
            SubtitleStream(
                index = i,
                language = f.language,
                title = f.label,
                codec = mime,
                isBitmap = mime in IMAGE_MIME
            )
        }
        _subtitleStreams.value = streams
        if (!_userChoseSource && streams.isNotEmpty()) {
            pickDefaultStream(streams)?.let { selectSubtitleStream(it) }
        }
    }

    /** 选择某条字幕流（文本轨→onCues 抓取；图片轨→交给 SubtitleView）。 */
    fun selectSubtitleStream(stream: SubtitleStream) {
        _userChoseSource = true
        _selectedStream.value = stream
        _subtitleSource.value = SubtitleSource.EMBEDDED
        _subtitles.value = emptyList()
        val g = textGroups.getOrNull(stream.index) ?: return
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .addOverride(TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
        player.trackSelectionParameters = params
    }

    /** 把 onCues 收到的 Cue 累积成绝对时间线（英文即时、中文延迟由覆盖层处理）。 */
    private fun ingestCues(cueGroup: CueGroup) {
        val selected = _selectedStream.value ?: return
        if (selected.isBitmap) return // 图片字幕交给 SubtitleView，不进文本覆盖层
        val incoming = mutableListOf<SubtitleItem>()
        for (cue in cueGroup.cues) {
            val text = cue.text?.toString() ?: continue
            if (text.isBlank()) continue
            val (en, zh) = BilingualCueSplitter.split(text)
            val startMs = if (cue.startTimeUs != C.TIME_UNSET) cue.startTimeUs / 1000 else player.currentPosition
            val endMs = if (cue.endTimeUs != C.TIME_UNSET) cue.endTimeUs / 1000 else startMs + 2000
            incoming += SubtitleItem(startMs, endMs, en, zh)
        }
        if (incoming.isEmpty()) return
        val merged = (_subtitles.value + incoming)
            .distinctBy { "${it.startTime}|${it.englishText}|${it.chineseText}" }
            .sortedBy { it.startTime }
        _subtitles.value = merged
    }

    /** 自动挑选（优先中文文本轨，其次任意文本轨，图片轨兜底）。 */
    fun selectDefaultEmbeddedStream() {
        val streams = _subtitleStreams.value
        pickDefaultStream(streams)?.let { selectSubtitleStream(it) }
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

// 图片类字幕 mime（无法以文本渲染，交给 SubtitleView）
private val IMAGE_MIME = setOf(
    MimeTypes.APPLICATION_PGS,
    MimeTypes.APPLICATION_DVBSUBS
)
