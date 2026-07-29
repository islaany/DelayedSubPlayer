package com.huqi.delayedsub.subtitle.renderer

import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleRendererTest {

    private val item = SubtitleItem(10_000, 12_000, "English line", "中文行")

    @Test
    fun `学习模式延迟前只显示英文`() {
        val before = SubtitleRenderer.resolve(listOf(item), 10_500, 3000, 1000, learningMode = true)
        assertEquals(SubtitleState.ENGLISH_VISIBLE, before.state)
        assertEquals("English line", before.english)
        assertEquals(null, before.chinese)
    }

    @Test
    fun `学习模式延迟后中文出现`() {
        val after = SubtitleRenderer.resolve(listOf(item), 11_500, 3000, 1000, learningMode = true)
        assertEquals(SubtitleState.CHINESE_VISIBLE, after.state)
        assertEquals("中文行", after.chinese)
    }

    @Test
    fun `普通模式开始即中英文同显`() {
        val d = SubtitleRenderer.resolve(listOf(item), 10_500, 3000, 1000, learningMode = false)
        assertEquals(SubtitleState.CHINESE_VISIBLE, d.state)
        assertEquals("中文行", d.chinese)
    }

    @Test
    fun `无活跃字幕返回 NONE`() {
        val d = SubtitleRenderer.resolve(listOf(item), 5_000, 3000, 1000, learningMode = true)
        assertEquals(SubtitleState.NONE, d.state)
    }
}
