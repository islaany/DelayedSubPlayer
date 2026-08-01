package com.huqi.delayedsub.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.text.TextOutput
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.data.database.VideoEntity
import com.huqi.delayedsub.data.subtitle.SubtitleRepository
import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.player.Media3Player
import com.huqi.delayedsub.subtitle.BilingualCueSplitter
import com.huqi.delayedsub.subtitle.EmbeddedTrack
import com.huqi.delayedsub.subtitle.SubtitleSource
import com.huqi.delayedsub.subtitle.model.SubtitleItem
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
 * - 视频内嵌字幕轨（[SubtitleSource.EMBEDDED]）：通过 ExoPlayer 的 [TextOutput] 抓取
 *   cue 并实时累积成 [SubtitleItem]，复用相同的渲染 / 延迟逻辑。
 *
 * 关键点：ExoPlayer 内置字幕默认被禁用（见 [Media3Player]），内嵌字幕不交给播放器自带
 * 渲染，而是由本类捕获后走我们自己的覆盖层，这样才能做到"中文比英文晚出现"。
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application, private val videoId: Long) :
    AndroidViewModel(app), TextOutput {

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

    /** 内嵌字幕轨是否已完成一次自动解析（避免重复自动启用）。 */
    private var _autoResolved = false

    val maxDelayMs: StateFlow<Long> = container.settingsRepository.maxDelayMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), DelayEngine.MAX_DELAY_DEFAULT_MS)

    val learningMode: StateFlow<Boolean> = container.settingsRepository.learningMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), true)

    private val trackListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val textTracks = tracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                .flatMap { g ->
                    (0 until g.length).map { i ->
                        val f = g.getTrackFormat(i)
                        EmbeddedTrack(g.mediaTrackGroup, i, f.language, f.label)
                    }
                }
            _embeddedTracks.value = textTracks
            if (!_autoResolved) {
                _autoResolved = true
                val hasExternal = !(_video.value?.subtitleUri.isNullOrBlank())
                // 没有外部字幕且视频自带字幕轨时，自动启用第一条内嵌字幕
                if (!hasExternal && textTracks.isNotEmpty()) {
                    selectEmbeddedTrack(textTracks.first())
                }
            }
        }
    }

    init {
        player.addListener(trackListener)
        player.addTextOutput(this)
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

    /**
     * [TextOutput] 回调：内嵌字幕轨解码后逐条吐出当前可见 cue。
     * 这里把 cue 增量累积成 [SubtitleItem] 列表（复用既有渲染 / 延迟逻辑）。
     *
     * 说明：内嵌字幕是"边播边解码"，无法预知整段时间线，因此采用实时累积策略——
     * 对正常向前播放完全准确；向后拖动到尚未播过的区段时会暂时缺字幕，重播即补回。
     */
    override fun onCues(cueGroup: CueGroup) {
        if (_subtitleSource.value != SubtitleSource.EMBEDDED) return
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
    }

    /** 选用某条内嵌字幕轨作为字幕来源。 */
    fun selectEmbeddedTrack(track: EmbeddedTrack) {
        _selectedEmbeddedTrack.value = track
        _subtitleSource.value = SubtitleSource.EMBEDDED
        _lastCueText = ""
        _subtitles.value = emptyList()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguage(track.language)
            .build()
    }

    /** 切回外部 .srt 字幕（若存在）。 */
    fun selectExternalSource() {
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
        player.removeTextOutput(this)
        player.removeListener(trackListener)
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
