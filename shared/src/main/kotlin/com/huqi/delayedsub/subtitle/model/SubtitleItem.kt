package com.huqi.delayedsub.subtitle.model

/**
 * 一条字幕的内部数据结构。
 *
 * @param startTime 字幕开始时间（毫秒）
 * @param endTime   字幕结束时间（毫秒）
 * @param englishText 英文文本（可能为空，例如纯中文行）
 * @param chineseText 中文文本（可能为空，例如纯英文行）
 */
data class SubtitleItem(
    val startTime: Long,
    val endTime: Long,
    val englishText: String,
    val chineseText: String
)
