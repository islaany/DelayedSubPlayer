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
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.data.database.VideoEntity
import com.huqi.delayedsub.data.subtitle.SubtitleRepository
import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.player.Media3Player
import com.huqi.delayedsub.subtitle.EmbeddedTrack
import com.huqi.delayedsub.subtitle.SubtitleSource
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import com.huqi.delayedsub.subtitle.parser.BilingualCueSplitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 播放页 ViewModel。
 *
 * 字幕来源有两种：
 * - 外部 .srt（[SubtitleSource.EXTERNAL]）：沿用既有解析链路，整段字幕一次性载入。
 * - 视频内嵌字幕轨（[SubtitleSource.EMBEDDED]）：通过 ExoPlayer 的
 *   [Player.Listener.onCues] 抓取 cue 并实时累积成 [SubtitleItem]，复用相同的渲染 /
 *   延迟逻辑。
 *
 * 字幕轨类型适配（"都适配"）：
 * - 文本轨（SRT / ASS / WebVTT / TTML 等）：cue 携带文本，走我们的覆盖层，中文延迟显示。
 * - 图片轨（PGS / SUP，蓝光内封常见）：ExoPlayer 自带 PgsParser 解码为带 bitmap 的 cue，
 *   由播放器自带 SubtitleView 直接渲染（图片无法拆中英，原样显示，无延迟）。
 * - 关键点：ExoPlayer 内置字幕默认被禁用（见 [Media3Player]），且我们对文本轨隐藏了
 *   SubtitleView 以防双重渲染；仅当选中的是图片轨时才显示 SubtitleView。
 *
 * 注意 onTracksChanged 不可再用 [Tracks.Group.isSupported] 过滤——图片字幕轨的 isSupported
 * 可能为 false，但 ExoPlayer 的 DefaultSubtitleParserFactory 已注册 PGS，原生支持解码。
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application, private val videoId: Long) : AndroidViewModel(app) {

    private val container = (app as DelayedSubApplication).container
    private val player: ExoPlayer = Media3Player.create(app)

    private val _subtitles = MutableStateFlow<List<SubtitleItem>>(emptyList())
    val subtitles: StateFlow<List<SubtitleItem>> = _subtitles.asStateFlow()

    private val _video = MutableStateFlow<VideoEntity?>(null)
    val video: StateFlow<VideoEntity?> = _video.asStateFlow()

    private val _embeddedTracks = MutableStateFlow<List<EmbeddedTrack>>(emptyList())
    val embeddedTracks: StateFlow<List<EmbeddedTrack>> = _embeddedTracks.asStateFlow()

    private val _subtitleSource = MutableStateFlow(SubtitleSource.NONE)
    val subtitleSource: StateFlow<SubtitleSource> = _subtitleSource.asStateFlow()

    private val _selectedEmbeddedTrack = MutableStateFlow<EmbeddedTrack?>(null)
    val selectedEmbeddedTrack: StateFlow<EmbeddedTrack?> = _selectedEmbeddedTrack.asStateFlow()

    /** 上一次已渲染的 cue 文本，用于增量去重。 */
    @Volatile
    private var _lastCueText: String = ""

    /** 用户是否已在设置面板手动选择过字幕来源（一旦手动选过就不再自动选轨，避免覆盖用户意图）。 */
    private var _userChoseSource = false

    val maxDelayMs: StateFlow<Long> = container.settingsRepository.maxDelayMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), DelayEngine.MAX_DELAY_DEFAULT_MS)

    val learningMode: StateFlow<Boolean> = container.settingsRepository.learningMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), true)

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            // 不过滤 isSupported：PGS 图片轨 isSupported 可能为 false，但 ExoPlayer 原生支持解码，
            // 过滤会直接漏掉它导致图片字幕永远无法选择 / 显示
            val textTracks = tracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .flatMap { g ->
                    (0 until g.length).map { i ->
                        val f = g.getTrackFormat(i)
                        EmbeddedTrack(
                            g.mediaTrackGroup,
                            i,
                            f.language,
                            f.label,
                            isPgs = f.sampleMimeType == MimeTypes.APPLICATION_PGS
                        )
                    }
                }
            _embeddedTracks.value = textTracks
            // 自动选轨：仅在用户尚未手动选择、且当前还没选中任何内嵌轨时进行。
            // 注意：onTracksChanged 可能在 prepare 完成前先以"空轨列表"触发一次，
            // 因此不能用「一次性标志」——否则真实字幕轨到达时就不会再自动选中，
            // 导致字幕轨检测到了却整段不显示（白屏）。
            if (!_userChoseSource && _selectedEmbeddedTrack.value == null) {
                val hasExternal = !(_video.value?.subtitleUri.isNullOrBlank())
                // 没有外部字幕且视频自带字幕轨时，自动启用一条内嵌字幕
                if (!hasExternal && textTracks.isNotEmpty()) {
                    applyEmbeddedTrack(pickDefaultTrack(textTracks))
                }
            }
        }

        /**
         * 内嵌字幕轨解码后逐条吐出当前可见 cue。
         * - 图片字幕（PGS）：由 ExoPlayer 自带 SubtitleView 渲染，这里直接跳过。
         * - 文本字幕：增量累积成 [SubtitleItem] 列表（复用既有渲染 / 延迟逻辑）。
         *
         * 说明：内嵌字幕是"边播边解码"，无法预知整段时间线，因此采用实时累积策略——
         * 对正常向前播放完全准确；向后拖动到尚未播过的区段时会暂时缺字幕，重播即补回。
         */
        override fun onCues(cueGroup: CueGroup) {
            if (_subtitleSource.value != SubtitleSource.EMBEDDED) return
            // 图片字幕（PGS/SUP）由 ExoPlayer 自带 SubtitleView 直接渲染，不走文本延迟逻辑
            if (_selectedEmbeddedTrack.value?.isPgs == true) return

            try {
                val text = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim()
                if (text == _lastCueText) return

                val pos = player.currentPosition
                val list = _subtitles.value.toMutableList()
                // 关闭上一条（结束时间 = 当前位置）
                if (list.isNotEmpty() && list.last().endTime == Long.MAX_VALUE) {
                    val last = list.last()
                    list[list.lastIndex] = last.copy(endTime = pos)
                }
                _lastCueText = text
                if (text.isNotEmpty()) {
                    val (en, zh) = BilingualCueSplitter.split(text)
                    if (en.isNotBlank() || zh.isNotBlank()) {
                        // 双语同轨：英文与中文填进同一条，渲染层按延迟分别显示
                        list.add(
                            SubtitleItem(
                                startTime = pos,
                                endTime = Long.MAX_VALUE,
                                englishText = en,
                                chineseText = zh
                            )
                        )
                    }
                }
                _subtitles.value = list
            } catch (_: Exception) {
                // 单条 cue 解析异常不应中断整个字幕流
            }
        }
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch {
            val v = container.videoRepository.get(videoId)
            _video.value = v
            v?.let {
                Media3Player.prepare(player, Uri.parse(it.videoUri), it.lastPositionMs)
                if (!it.subtitleUri.isNullOrBlank()) {
                    runCatching { container.subtitleRepository.load(getApplication(), Uri.parse(it.subtitleUri)) }
                        .onSuccess { parsed ->
                            _subtitles.value = parsed
                            _subtitleSource.value = SubtitleSource.EXTERNAL
                        }
                }
            }
        }
    }

    /** 真正落实选轨（更新状态 + 强制 TrackSelectionOverride）。不标记用户已手动选择。 */
    private fun applyEmbeddedTrack(track: EmbeddedTrack) {
        _selectedEmbeddedTrack.value = track
        _subtitleSource.value = SubtitleSource.EMBEDDED
        _lastCueText = ""
        _subtitles.value = emptyList()
        // 用 TrackSelectionOverride 直接强制选中这条轨，避免语言标签为 und/null 时
        // setPreferredTextLanguage 匹配不到任何轨、导致字幕整段不解码（之前白屏的根因）
        val override = TrackSelectionOverride(track.trackGroup, track.index)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .addOverride(override)
            .build()
    }

    /** 用户（或设置面板）选用某条内嵌字幕轨作为字幕来源。 */
    fun selectEmbeddedTrack(track: EmbeddedTrack) {
        _userChoseSource = true
        applyEmbeddedTrack(track)
    }

    /** 选用自动挑选的（优先中文）内嵌字幕轨，标记为用户已选择。 */
    fun selectDefaultEmbeddedTrack() {
        val tracks = _embeddedTracks.value
        if (tracks.isNotEmpty()) {
            _userChoseSource = true
            applyEmbeddedTrack(pickDefaultTrack(tracks))
        }
    }

    /** 自动选轨：优先中文文本轨（走延迟学习逻辑），其次任意文本轨，兜底第一条（可能即图片轨）。 */
    private fun pickDefaultTrack(tracks: List<EmbeddedTrack>): EmbeddedTrack {
        tracks.firstOrNull { isChineseTrack(it.language) && !it.isPgs }?.let { return it }
        tracks.firstOrNull { !it.isPgs }?.let { return it }
        return tracks.first()
    }

    private fun isChineseTrack(lang: String?): Boolean {
        if (lang.isNullOrBlank()) return false
        val l = lang.lowercase()
        return l.startsWith("zh") || l.startsWith("chi") || l.contains("chinese")
    }

    /** 切回外部 .srt 字幕（若存在）。 */
    fun selectExternalSource() {
        _userChoseSource = true
        _subtitleSource.value = SubtitleSource.EXTERNAL
        _selectedEmbeddedTrack.value = null
        _lastCueText = ""
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguage(null)
            .build()
        _subtitles.value = emptyList()
        val uri = _video.value?.subtitleUri
        if (!uri.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching { container.subtitleRepository.load(getApplication(), Uri.parse(uri)) }
                    .onSuccess { _subtitles.value = it }
            }
        }
    }

    /** 完全关闭字幕。 */
    fun selectNoSubtitle() {
        _userChoseSource = true
        _subtitleSource.value = SubtitleSource.NONE
        _selectedEmbeddedTrack.value = null
        _lastCueText = ""
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguage(null)
            .build()
        _subtitles.value = emptyList()
    }

    fun setLearningMode(on: Boolean) =
        viewModelScope.launch { container.settingsRepository.setLearningMode(on) }

    fun setMaxDelay(ms: Long) =
        viewModelScope.launch { container.settingsRepository.setMaxDelayMs(ms) }

    val exoPlayer: ExoPlayer get() = player

    override fun onCleared() {
        val pos = player.currentPosition
        player.removeListener(playerListener)
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
