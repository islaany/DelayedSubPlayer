package com.huqi.delayedsub.subtitle

/**
 * 字幕来源类型。
 * - [NONE]：不显示字幕。
 * - [EXTERNAL]：用户单独提供的 .srt/.vtt 等外部字幕文件。
 * - [EMBEDDED]：从视频「内嵌字幕轨」抽取得到的字幕（经 ffmpeg 抽轨为文本后渲染）。
 */
enum class SubtitleSource {
    NONE,
    EXTERNAL,
    EMBEDDED
}
