package com.huqi.delayedsub.subtitle

/**
 * 字幕来源类型。
 * - [NONE]：不显示字幕。
 * - [EXTERNAL]：用户单独提供的 .srt/.vtt 等外部字幕文件。
 * - [EMBEDDED]：从视频「内嵌字幕轨」由播放器抓取得到的字幕（ExoPlayer 的 onCues 实时吐出，
 *   本地与网络链接都稳定；图片字幕轨交给播放器内置视图渲染）。
 */
enum class SubtitleSource {
    NONE,
    EXTERNAL,
    EMBEDDED
}
