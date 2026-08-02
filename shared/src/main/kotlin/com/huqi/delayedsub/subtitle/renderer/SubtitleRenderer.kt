package com.huqi.delayedsub.subtitle.renderer

import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.subtitle.model.SubtitleItem

/**
 * 字幕显示状态。
 */
enum class SubtitleState {
    /** 当前无字幕 */
    NONE,
    /** 英文已显示，中文隐藏（学习模式刚开始） */
    ENGLISH_VISIBLE,
    /** 英文 + 中文都已显示 */
    CHINESE_VISIBLE
}

/**
 * 某一播放时刻应渲染的字幕内容。
 */
data class SubtitleDisplay(
    val english: String?,
    val chinese: String?,
    val state: SubtitleState
)

/**
 * 字幕状态机：给定当前播放位置，纯函数式地决定显示什么。
 *
 * - 普通模式：字幕一开始，英文与中文同时显示。
 * - 学习模式：英文在开始时显示；中文延迟 [DelayEngine.computeDelay] 毫秒后显示。
 *
 * 该计算只依赖播放位置，因此暂停 / 拖进度条 / 倍速全部天然正确。
 */
object SubtitleRenderer {

    fun resolve(
        items: List<SubtitleItem>,
        positionMs: Long,
        maxDelayMs: Long,
        minDelayMs: Long,
        learningMode: Boolean
    ): SubtitleDisplay {
        val item = items.lastOrNull { positionMs in it.startTime..it.endTime }
            ?: return SubtitleDisplay(null, null, SubtitleState.NONE)

        val englishVisible = positionMs >= item.startTime
        val delay = DelayEngine.computeDelay(
            durationMs = item.endTime - item.startTime,
            maxDelayMs = maxDelayMs,
            minDelayMs = minDelayMs
        )
        val chineseVisible = if (learningMode) {
            positionMs >= item.startTime + delay
        } else {
            englishVisible
        }

        val english = if (englishVisible && item.englishText.isNotBlank()) item.englishText else null
        val chinese = if (chineseVisible && item.chineseText.isNotBlank()) item.chineseText else null

        val state = when {
            english == null -> SubtitleState.NONE
            chinese == null -> SubtitleState.ENGLISH_VISIBLE
            else -> SubtitleState.CHINESE_VISIBLE
        }

        return SubtitleDisplay(english, chinese, state)
    }
}
