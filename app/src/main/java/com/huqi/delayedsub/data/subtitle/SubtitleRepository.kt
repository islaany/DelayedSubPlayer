package com.huqi.delayedsub.data.subtitle

import android.content.Context
import android.net.Uri
import com.huqi.delayedsub.subtitle.model.SubtitleItem
import com.huqi.delayedsub.subtitle.parser.SrtSubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从外部 URI（用户通过 SAF 选择的 .srt 文件）加载并解析字幕。
 */
class SubtitleRepository {

    suspend fun load(context: Context, uri: Uri): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法打开字幕文件: $uri")
        SrtSubtitleParser.parse(bytes)
    }
}
