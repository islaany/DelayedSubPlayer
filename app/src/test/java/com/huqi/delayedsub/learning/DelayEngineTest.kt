package com.huqi.delayedsub.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class DelayEngineTest {

    @Test
    fun `2s 字幕 delay=1s`() = assertEquals(1000L, DelayEngine.computeDelay(2000))

    @Test
    fun `6s 字幕 delay=3s`() = assertEquals(3000L, DelayEngine.computeDelay(6000))

    @Test
    fun `20s 字幕被 MAX 截断为 3s`() = assertEquals(3000L, DelayEngine.computeDelay(20000))

    @Test
    fun `极短 1s 字幕回退为时长一半`() = assertEquals(500L, DelayEngine.computeDelay(1000))

    @Test
    fun `零时长返回 0`() = assertEquals(0L, DelayEngine.computeDelay(0))

    @Test
    fun `自定义 maxDelay 生效`() = assertEquals(5000L, DelayEngine.computeDelay(20000, maxDelayMs = 5000))
}
