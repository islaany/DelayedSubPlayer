package com.huqi.delayedsub.learning

/**
 * 自适应字幕延迟引擎。
 *
 * 公式：
 *     duration = endTime - startTime
 *     delay = max( min( duration * 0.5, MAX_DELAY ), MIN_DELAY )
 *
 * 例：
 *   - 2s 字幕 -> delay = 1000ms
 *   - 6s 字幕 -> delay = 3000ms
 *   - 20s 字幕 -> delay = 3000ms（被 MAX_DELAY 截断）
 *
 * 边界：当 duration 极短（≤ 2s）时，公式会算出 delay ≥ duration，
 * 中文将永远不会显示。此时回退为 duration / 2，保证中文至少显示后半段。
 */
object DelayEngine {

    const val MAX_DELAY_DEFAULT_MS = 3000L
    const val MIN_DELAY_DEFAULT_MS = 1000L

    fun computeDelay(
        durationMs: Long,
        maxDelayMs: Long = MAX_DELAY_DEFAULT_MS,
        minDelayMs: Long = MIN_DELAY_DEFAULT_MS
    ): Long {
        if (durationMs <= 0) return 0L
        val base = minOf(durationMs / 2, maxDelayMs)
        val clamped = maxOf(base, minDelayMs)
        // 极短字幕：延迟超过时长则中文永不显示，回退为时长一半
        return if (clamped >= durationMs) durationMs / 2 else clamped
    }
}
