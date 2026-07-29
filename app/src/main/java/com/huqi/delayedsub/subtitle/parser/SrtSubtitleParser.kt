package com.huqi.delayedsub.subtitle.parser

import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import java.nio.ByteBuffer

/**
 * SRT 字幕解析器。
 *
 * 复用 AndroidX Media3 内置的 [SubripParser]（ExoPlayer 官方解析器，成熟稳定），
 * 不自己造轮子。解析后通过字幕事件时间轴重建每条 cue 的 [start, end] 区间，
 * 再用 [BilingualCueSplitter] 把文本拆成英文 / 中文两部分。
 *
 * 第二阶段支持 .ass / .ssa 时，只需把这里的 [SubripParser] 换成 Media3 的
 * [androidx.media3.extractor.text.ssa.SsaParser]，上层结构无需改动。
 */
object SrtSubtitleParser {

    fun parse(bytes: ByteArray): List<SubtitleItem> {
        val parser = SubripParser()
        val subtitle = parser.parse(
            ByteBuffer.wrap(bytes),
            SubtitleParser.OutputOptions.ALL,
            SubtitleParser.DecodeTricks.DEFAULT
        )

        val count = subtitle.eventTimeCount
        val items = mutableListOf<SubtitleItem>()
        // 以 cue 文本为 key，记录当前仍"活跃"的 cue 的起始时间
        val activeStart = LinkedHashMap<String, Long>()
        var lastTime = 0L

        for (i in 0 until count) {
            val t = subtitle.getEventTime(i)
            lastTime = t

            val present = mutableSetOf<String>()
            for (cue in subtitle.getCues(t)) {
                val key = cue.text?.toString() ?: continue
                present += key
            }

            // 结束的 cue：用 (start, 当前事件时间) 收尾
            for ((key, start) in activeStart.toList()) {
                if (key !in present) {
                    items += build(key, start, t)
                    activeStart.remove(key)
                }
            }
            // 新开始的 cue
            for (key in present) {
                if (key !in activeStart) activeStart[key] = t
            }
        }

        // 收尾仍活跃的 cue
        for ((key, start) in activeStart) {
            items += build(key, start, lastTime)
        }

        return items.sortedBy { it.startTime }
    }

    private fun build(rawText: String, start: Long, end: Long): SubtitleItem {
        val (english, chinese) = BilingualCueSplitter.split(rawText)
        return SubtitleItem(
            startTime = start,
            endTime = end,
            englishText = english,
            chineseText = chinese
        )
    }
}
