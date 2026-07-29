package com.huqi.delayedsub.subtitle.parser

import java.util.regex.Pattern

/**
 * 把一条字幕的原始文本拆分成「英文」和「中文」两部分。
 *
 * 判定规则：按换行拆分后，凡是含有 CJK 表意文字或中日韩标点的行归为中文，
 * 其余归为英文。这样既能处理「一行英文 + 一行中文」的双语字幕，
 * 也能处理纯英文 / 纯中文字幕（对应部分留空）。
 */
object BilingualCueSplitter {

    // 匹配 CJK 表意文字（含扩展 A）以及常见的中日韩标点 / 全角符号
    private val CJK = Pattern.compile("[\\u3400-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]")

    fun split(text: CharSequence?): Pair<String, String> {
        if (text.isNullOrBlank()) return "" to ""

        val english = mutableListOf<String>()
        val chinese = mutableListOf<String>()

        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (CJK.matcher(line).find()) chinese += line else english += line
        }

        return english.joinToString("\n") to chinese.joinToString("\n")
    }
}
