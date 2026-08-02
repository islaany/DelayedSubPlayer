package com.huqi.delayedsub.subtitle.parser

import com.huqi.delayedsub.subtitle.model.SubtitleItem

/**
 * SRT 字幕解析器（自包含实现，与平台无关）。
 *
 * 不依赖 AndroidX Media3 内部的 SubripParser，避免其内部 API 破坏性变更。
 * 直接按 SRT 规范解析，行为稳定。解析后用 [BilingualCueSplitter] 拆出英文 / 中文。
 *
 * 兼容点：
 * - UTF-8（自动剥离 BOM）
 * - 时间码分隔符 `,` 与 `.` 都接受（部分工具导出用 `.`）
 * - 空行切片，且兼容 `\r\n` / `\n`
 */
object SrtSubtitleParser {

    private val TIMECODE = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    fun parse(bytes: ByteArray): List<SubtitleItem> {
        val raw = String(bytes, Charsets.UTF_8).replace("\uFEFF", "")
        return parse(raw)
    }

    fun parse(text: String): List<SubtitleItem> {
        val items = mutableListOf<SubtitleItem>()
        // 以空行切块
        val blocks = text.split(Regex("""\r?\n\r?\n"""))
        for (block in blocks) {
            val lines = block.split("\n").map { it.trimEnd('\r') }
            // 找到时间码行
            var tcLine = -1
            for (i in lines.indices) {
                if (TIMECODE.containsMatchIn(lines[i])) {
                    tcLine = i
                    break
                }
            }
            if (tcLine < 0) continue

            val m = TIMECODE.find(lines[tcLine]) ?: continue
            val (sh, sm, ss, sms, eh, em, es, ems) = m.destructured
            val start = toMs(sh, sm, ss, sms)
            val end = toMs(eh, em, es, ems)

            val body = lines.drop(tcLine + 1).joinToString("\n").trim()
            if (body.isEmpty()) continue

            val (english, chinese) = BilingualCueSplitter.split(body)
            items += SubtitleItem(
                startTime = start,
                endTime = end,
                englishText = english,
                chineseText = chinese
            )
        }
        return items.sortedBy { it.startTime }
    }

    private fun toMs(h: String, m: String, s: String, ms: String): Long {
        val hh = h.toLongOrNull() ?: 0L
        val mm = m.toLongOrNull() ?: 0L
        val ss = s.toLongOrNull() ?: 0L
        // 补足到 3 位毫秒（"1" -> 100，"50" -> 500，"000" -> 000）
        val milli = (ms + "000").take(3).toLongOrNull() ?: 0L
        return (hh * 3600 + mm * 60 + ss) * 1000 + milli
    }
}
