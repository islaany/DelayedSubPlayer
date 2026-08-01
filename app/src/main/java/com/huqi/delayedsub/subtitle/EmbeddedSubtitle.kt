package com.huqi.delayedsub.subtitle

import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup

/**
 * 视频容器内嵌的一条字幕轨。
 *
 * @param trackGroup Media3 的轨道组（用于选中该轨）
 * @param index      在轨道组内的下标
 * @param language   轨道语言代码（如 "en" / "zh" / "und"），可能为 null
 * @param label      轨道可读标签（部分封装会带，如 "English"），可能为 null
 * @param isPgs      是否为图片字幕轨（PGS/SUP）。图片字幕无法拆出中英文本，
 *                   由 ExoPlayer 自带 SubtitleView 直接渲染，不走我们的延迟逻辑
 */
data class EmbeddedTrack(
    val trackGroup: TrackGroup,
    val index: Int,
    val language: String?,
    val label: String?,
    val isPgs: Boolean = false
) {
    /** 界面展示名：优先 label，其次 language，都没有则回退为"字幕轨 N"；图片轨追加（图片）标记。 */
    val displayName: String
        get() {
            val base = (label ?: language ?: "").trim()
            val name = if (base.isBlank()) "字幕轨 ${index + 1}" else base
            return if (isPgs) "$name（图片）" else name
        }
}

/** 当前字幕数据来源。 */
enum class SubtitleSource {
    /** 不显示字幕 */
    NONE,
    /** 来自视频内嵌字幕轨（App 自行解析渲染，或交给 ExoPlayer 渲染图片轨） */
    EMBEDDED,
    /** 来自用户单独选择的 .srt 文件 */
    EXTERNAL
}
