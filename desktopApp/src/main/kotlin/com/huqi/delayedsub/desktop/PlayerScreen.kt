package com.huqi.delayedsub.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FileChooser
import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.subtitle.renderer.SubtitleDisplay
import com.huqi.delayedsub.subtitle.renderer.SubtitleRenderer
import java.io.File

@Composable
fun PlayerScreen(model: AppModel) {
    val videoPath = model.videoPath
    val subtitles = model.subtitles
    val positionMs = model.positionMs
    val durationMs = model.durationMs
    val learning = model.learningMode
    val maxDelay = model.maxDelayMs
    val status = model.status
    val vlcError = model.vlcError
    val streams = model.subtitleStreams
    val selected = model.selectedStream

    var url by remember { mutableStateOf("") }

    val display: SubtitleDisplay = remember(subtitles, positionMs, maxDelay, learning) {
        SubtitleRenderer.resolve(subtitles, positionMs, maxDelay, DelayEngine.MIN_DELAY_DEFAULT_MS, learning)
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("延迟字幕学习播放器 · 桌面版", color = Color.White, fontSize = 18.sp)
            Text(status, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (vlcError != null) {
                Text(vlcError!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            // 视频 + 字幕覆盖层
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                if (videoPath != null && vlcError == null) {
                    key(videoPath) {
                        SwingPanel(
                            factory = { model.playerView() ?: object : java.awt.Component() {} },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未加载视频", color = Color.Gray)
                    }
                }
                // 字幕覆盖层（英文即时 / 中文延迟）
                Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        val english = display.english
                        val chinese = display.chinese
                        if (english != null) {
                            Text(english, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                        }
                        if (chinese != null) {
                            Text(
                                chinese,
                                color = Color(0xFFFFE082),
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 打开：链接 + 文件
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    url,
                    { url = it },
                    placeholder = { Text("粘贴视频网络链接（http/https）") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { if (url.isNotBlank()) model.loadVideo(url) }) { Text("打开链接") }
                Button(onClick = {
                    val f = FileChooser().showOpenDialog()
                    if (f != null) model.loadVideo(f.absolutePath)
                }) { Text("打开文件") }
            }

            // 控制条
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { model.playPause() }) { Text(if (model.isPlaying) "暂停" else "播放") }
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onValueChange = { model.seek((it * durationMs).toLong()) },
                    modifier = Modifier.weight(1f)
                )
                Text(fmt(positionMs) + " / " + fmt(durationMs), color = Color.White, fontSize = 12.sp)
            }

            // 字幕轨选择
            if (streams.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("字幕轨：", color = Color.White, fontSize = 12.sp)
                    streams.forEach { s ->
                        FilterChip(
                            selected = selected == s,
                            onClick = { model.selectStream(s) },
                            label = { Text(s.displayName) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            // 设置：学习模式 + 延迟
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("学习模式（中文延迟）", color = Color.White, fontSize = 12.sp)
                Switch(checked = learning, onCheckedChange = { model.setLearning(it) })
                Spacer(Modifier.width(16.dp))
                Text("延迟：", color = Color.White, fontSize = 12.sp)
                for (ms in listOf(2000L, 3000L, 5000L)) {
                    FilterChip(
                        selected = maxDelay == ms,
                        onClick = { model.setMaxDelay(ms) },
                        label = { Text("${ms / 1000}s") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
