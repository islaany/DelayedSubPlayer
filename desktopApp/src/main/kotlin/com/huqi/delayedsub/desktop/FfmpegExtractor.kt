package com.huqi.delayedsub.desktop

import com.huqi.delayedsub.subtitle.SubtitleStream
import java.io.File
import kotlin.text.RegexOption

/**
 * 用内嵌/系统 ffmpeg 从视频（本地或网络链接）探测并抽取字幕轨。
 *
 * 与 Android 端思路一致：把字幕轨抽为 SRT，交给 shared 里的解析 + 双语拆分 + 延迟覆盖层。
 * Windows 端可以随应用打包一个 ffmpeg.exe（放在 resources），也可走 PATH / 同目录。
 */
object FfmpegExtractor {

    private val IMAGE_CODECS = setOf(
        "hdmv_pgs_subtitle", "pgssub", "dvd_subtitle", "dvb_subtitle", "dvbsub", "xsub", "pgs"
    )

    private fun ffmpegPath(): String? {
        // 1) 从 jar 资源解出的临时文件（CI 会把 ffmpeg.exe 打进 resources）
        runCatching {
            FfmpegExtractor::class.java.getResourceAsStream("/ffmpeg.exe")?.use { res ->
                val tmp = File.createTempFile("ffmpeg", ".exe").apply { deleteOnExit() }
                tmp.outputStream().use { out -> res.copyTo(out) }
                if (tmp.length() > 0) return tmp.absolutePath
            }
        }
        // 2) PATH 中的 ffmpeg.exe
        System.getenv("PATH")?.split(File.pathSeparator)?.firstNotNullOfOrNull { dir ->
            File(dir, "ffmpeg.exe").takeIf { it.exists() }
        }?.let { return it.absolutePath }
        // 3) 当前工作目录
        File("ffmpeg.exe").takeIf { it.exists() }?.let { return it.absolutePath }
        return null
    }

    /** 探测字幕流。任何异常都返回空列表（不阻塞播放）。 */
    fun probe(source: String): List<SubtitleStream> {
        val ff = ffmpegPath() ?: return emptyList()
        return runCatching {
            val proc = ProcessBuilder(ff, "-hide_banner", "-i", source)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            parse(out)
        }.getOrDefault(emptyList())
    }

    /** 抽取指定字幕流为 SRT 文本文件。成功返回 true。 */
    fun extract(source: String, index: Int, out: File): Boolean {
        val ff = ffmpegPath() ?: return false
        return runCatching {
            val proc = ProcessBuilder(
                ff, "-hide_banner", "-y", "-i", source,
                "-map", "0:s:$index", "-f", "srt", out.absolutePath
            ).redirectErrorStream(true).start()
            proc.waitFor()
            out.exists() && out.length() > 0
        }.getOrDefault(false)
    }

    private fun parse(log: String): List<SubtitleStream> {
        val streams = mutableListOf<SubtitleStream>()
        // 形如：  Stream #0:2(eng): Subtitle: subrip
        //        Stream #0:3(chi): Subtitle: ass (ssa) (default)
        //        Stream #0:4: Subtitle: hdmv_pgs_subtitle
        val re = Regex(
            """Stream\s+#\d+:(\d+)(?:\(([^)]*)\))?:.*?Subtitle:\s*([\w]+)""",
            RegexOption.IGNORE_CASE
        )
        for (m in re.findAll(log)) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            val lang = m.groupValues[2].takeIf { it.isNotBlank() }
            val codec = m.groupValues[3].lowercase()
            streams += SubtitleStream(idx, lang, null, codec, codec in IMAGE_CODECS)
        }
        return streams.distinctBy { it.index }
    }
}
