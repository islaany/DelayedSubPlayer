package com.huqi.delayedsub.subtitle

/**
 * 视频中一条字幕轨的中立描述（与具体播放器 / 平台无关）。
 *
 * 由 [SubtitleExtractor] 探测得到，跨 Android / Windows 共用，用于在 UI 上
 * 列出可选择的字幕轨，并作为抽取目标。
 *
 * @param index   字幕流下标，对应 ffmpeg 的 `0:s:<index>`（即 `Stream #0:N` 中的 N）。
 * @param language 语言代码（如 "eng" / "chi"），可能为 null。
 * @param title   轨道标题（若有），可为 null。
 * @param codec   原始编码名（如 "subrip" / "ass" / "hdmv_pgs_subtitle"）。
 * @param isBitmap 是否为图片字幕（PGS/SUP/DVD/DVB），图片字幕无法转为文本延迟。
 */
data class SubtitleStream(
    val index: Int,
    val language: String?,
    val title: String?,
    val codec: String,
    val isBitmap: Boolean
) {
    /** UI 展示用的简短标签。 */
    val displayName: String
        get() {
            val lang = language?.takeIf { it.isNotBlank() }?.uppercase() ?: "未知"
            val kind = if (isBitmap) "（图片）" else ""
            return "字幕 #$index · $lang$kind"
        }
}
