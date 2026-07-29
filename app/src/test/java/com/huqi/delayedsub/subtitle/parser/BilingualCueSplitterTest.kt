package com.huqi.delayedsub.subtitle.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class BilingualCueSplitterTest {

    @Test
    fun `双语一行英文一行中文`() {
        val (en, zh) = BilingualCueSplitter.split("Hey, Carl.\n嘿，卡尔。")
        assertEquals("Hey, Carl.", en)
        assertEquals("嘿，卡尔。", zh)
    }

    @Test
    fun `纯英文字幕中文为空`() {
        val (en, zh) = BilingualCueSplitter.split("Just wait here.")
        assertEquals("Just wait here.", en)
        assertEquals("", zh)
    }

    @Test
    fun `纯中文字幕英文为空`() {
        val (en, zh) = BilingualCueSplitter.split("走吧。")
        assertEquals("", en)
        assertEquals("走吧。", zh)
    }

    @Test
    fun `多行英文合并单行中文`() {
        val (en, zh) = BilingualCueSplitter.split("Line one.\nLine two.\n中文行")
        assertEquals("Line one.\nLine two.", en)
        assertEquals("中文行", zh)
    }

    @Test
    fun `空文本返回双空`() {
        val (en, zh) = BilingualCueSplitter.split("")
        assertEquals("" to "", en to zh)
    }
}
