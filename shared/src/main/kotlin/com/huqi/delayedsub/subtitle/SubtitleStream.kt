package com.huqi.delayedsub.subtitle

/**
 * 视频中一条字幕轨的中立描述（与具体播放器 / 平台无关）。
 *
 * 由播放器枚举得到（Android 端用 ExoPlayer 的 [androidx.media3.common.Tracks]，
 * Windows 端等价枚举），跨 Android / Windows 共用，用于在 UI 上列出可选择的字幕轨。
 *
 * @param index   字幕流在「文本/图片字幕轨列表」中的下标，对应 UI 选轨与目标轨。
 * @param language 语言代码（如 "eng" / "chi"），可能为 null。
 * @param title   轨道标题（若有），可为 null。
 * @param codec   原始 mime 类型（如 "application/subrip" / "text/ssa" / "application/pgs"）。
 * @param isBitmap 是否为图片字幕（PGS/DVBSUB），图片字幕无法转为文本延迟，交给播放器内置视图渲染。
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
