package com.huqi.delayedsub.subtitle

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * 用 ffmpeg-kit 从视频（本地文件或网络链接）探测并抽取字幕轨。
 *
 * 动机：ExoPlayer 的 onCues 对「流式 / 网络链接」视频常常识别不到内嵌字幕轨，
 * 导致字幕空白。改为「先抽轨为文本 SRT，再用我们自己的延迟覆盖层渲染」，
 * 本地与流式都稳定。Windows 端用 ffmpeg CLI 做等价实现，思路一致。
 */
object SubtitleExtractor {

    private val IMAGE_CODECS = setOf(
        "hdmv_pgs_subtitle", "pgssub", "dvd_subtitle", "dvb_subtitle", "dvbsub", "xsub", "pgs"
    )

    /** 探测字幕流。任何异常都返回空列表（不阻塞播放）。 */
    fun probe(source: String): List<SubtitleStream> {
        return runCatching {
            // -i 仅探测，无需输出文件；ffmpeg 会把流信息打到日志
            val session = FFmpegKit.execute("-hide_banner -i \"$source\"")
            parseStreams(session.output ?: "")
        }.getOrDefault(emptyList())
    }

    /** 抽取指定字幕流为 SRT 文本文件。成功返回 true。 */
    fun extract(source: String, streamIndex: Int, out: File): Boolean {
        return runCatching {
            val rc = FFmpegKit.execute(
                "-hide_banner -y -i \"$source\" -map 0:s:$streamIndex -f srt \"${out.absolutePath}\""
            )
            ReturnCode.isSuccess(rc.returnCode) && out.exists() && out.length() > 0
        }.getOrDefault(false)
    }

    /**
     * 把视频 uri 解析为 ffmpeg 可读的路径：
     * - http(s):// 直接返回（ffmpeg 可远程读取，联网权限已申请）
     * - file:// 取路径
     * - content:// 复制到应用缓存后返回路径（ffmpeg 无法直接读 content scheme）
     */
    fun resolvePath(context: Context, uriString: String): String {
        if (uriString.startsWith("http://", true) || uriString.startsWith("https://", true)) {
            return uriString
        }
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "file" -> uri.path ?: uriString
            "content" -> runCatching {
                val dst = File(context.cacheDir, "video_src_${System.currentTimeMillis()}.tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { out -> input.copyTo(out) }
                }
                dst.absolutePath
            }.getOrDefault(uriString)
            else -> uriString
        }
    }

    private fun parseStreams(log: String): List<SubtitleStream> {
        val streams = mutableListOf<SubtitleStream>()
        // 形如：  Stream #0:2(eng): Subtitle: subrip
        //        Stream #0:3(chi): Subtitle: ass (ssa) (default)
        //        Stream #0:4: Subtitle: hdmv_pgs_subtitle
        val re = Regex(
            """Stream\s+#\d+:(\d+)(?:\(([^)]*)\))?:.*?Subtitle:\s*([\w]+)""",
            RegexOption.IGNORECASE
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
