package com.huqi.delayedsub.subtitle.parser

import java.util.regex.Pattern

/**
 * 把一条字幕的原始文本拆分成「英文」和「中文」两部分。
 *
 * 采用**按字符过滤**的策略（而非简单的按行拆分），因此对两种常见布局都鲁棒：
 * - 换行布局：   "Hey, Carl.\n嘿，卡尔。"
 * - 空格拼接布局："Hey, Carl. 嘿，卡尔。"（部分解析器会把换行压成空格）
 *
 * 规则：含有 CJK 表意文字或中日韩标点的字符归为中文，其余（含英文字母、数字、
 * 西文标点、换行/空格）归为英文。两部分各自 trim 后返回。
 */
object BilingualCueSplitter {

    // CJK 表意文字（含扩展 A）以及常见的中日韩标点 / 全角符号
    private val CJK = Pattern.compile("[\\u3400-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]")

    private fun isCjk(ch: Char): Boolean = CJK.matcher(ch.toString()).find()

    fun split(text: CharSequence?): Pair<String, String> {
        if (text.isNullOrBlank()) return "" to ""

        val english = StringBuilder()
        val chinese = StringBuilder()
        for (ch in text) {
            if (isCjk(ch)) chinese.append(ch) else english.append(ch)
        }
        return english.toString().trim() to chinese.toString().trim()
    }
}
