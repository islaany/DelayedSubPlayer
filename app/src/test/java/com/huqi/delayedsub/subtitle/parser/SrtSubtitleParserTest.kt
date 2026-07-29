package com.huqi.delayedsub.subtitle.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtSubtitleParserTest {

    @Test
    fun `解析双语 SRT 并拆分英中`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            Hey, Carl.
            嘿，卡尔。

            2
            00:00:04,000 --> 00:00:06,500
            Run!
            快跑！

        """.trimIndent()

        val items = SrtSubtitleParser.parse(srt.toByteArray(Charsets.UTF_8))

        assertEquals(2, items.size)

        assertEquals(1000, items[0].startTime)
        assertEquals(3000, items[0].endTime)
        assertEquals("Hey, Carl.", items[0].englishText)
        assertEquals("嘿，卡尔。", items[0].chineseText)

        assertEquals(4000, items[1].startTime)
        assertEquals(6500, items[1].endTime)
        assertEquals("Run!", items[1].englishText)
        assertEquals("快跑！", items[1].chineseText)
    }

    @Test
    fun `纯英文字幕中文为空`() {
        val srt = "1\n00:00:00,500 --> 00:00:02,000\nJust a test.\n"
        val items = SrtSubtitleParser.parse(srt.toByteArray(Charsets.UTF_8))
        assertEquals(1, items.size)
        assertEquals("Just a test.", items[0].englishText)
        assertEquals("", items[0].chineseText)
    }
}
