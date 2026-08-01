package com.huqi.delayedsub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.View
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.navigation.NavController
import com.huqi.delayedsub.subtitle.EmbeddedTrack
import com.huqi.delayedsub.subtitle.SubtitleSource
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.learning.DelayEngine
import com.huqi.delayedsub.subtitle.renderer.SubtitleDisplay
import com.huqi.delayedsub.subtitle.renderer.SubtitleRenderer
import com.huqi.delayedsub.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(videoId: Long, navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as DelayedSubApplication
    val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(app, videoId))

    val video by vm.video.collectAsState(initial = null)
    val subtitles by vm.subtitles.collectAsState(initial = emptyList())
    val maxDelay by vm.maxDelayMs.collectAsState(initial = DelayEngine.MAX_DELAY_DEFAULT_MS)
    val learning by vm.learningMode.collectAsState(initial = true)
    val subtitleSource by vm.subtitleSource.collectAsState(initial = SubtitleSource.NONE)
    val embeddedTracks by vm.embeddedTracks.collectAsState(initial = emptyList())
    val selectedTrack by vm.selectedEmbeddedTrack.collectAsState(initial = null)
    val hasExternal = video?.subtitleUri != null

    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    val player = vm.exoPlayer
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            playing = player.isPlaying
            delay(100)
        }
    }

    val display = remember(subtitles, position, maxDelay, learning) {
        SubtitleRenderer.resolve(subtitles, position, maxDelay, DelayEngine.MIN_DELAY_DEFAULT_MS, learning)
    }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                video?.title ?: "播放",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setPlayer(player)
                        // 内嵌字幕由我们自己的覆盖层渲染，隐藏播放器自带 SubtitleView 避免双重显示
                        findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)?.visibility = View.GONE
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            SubtitleOverlay(display)
        }

        PlayerControls(
            player = player,
            position = position,
            playing = playing,
            learning = learning,
            maxDelay = maxDelay,
            showSettings = showSettings,
            onToggleSettings = { showSettings = !showSettings },
            onSetLearning = vm::setLearningMode,
            onSetMaxDelay = vm::setMaxDelay,
            subtitleSource = subtitleSource,
            embeddedTracks = embeddedTracks,
            selectedTrack = selectedTrack,
            hasExternal = hasExternal,
            onSelectNoSubtitle = vm::selectNoSubtitle,
            onSelectEmbedded = vm::selectEmbeddedTrack,
            onSelectExternal = vm::selectExternalSource
        )
    }
}

@Composable
private fun SubtitleOverlay(display: SubtitleDisplay) {
    Box(
        Modifier.fillMaxSize().padding(bottom = 56.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            if (display.english != null) {
                Text(
                    text = display.english,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (display.chinese != null) {
                Text(
                    text = display.chinese,
                    color = Color(0xFFFFE082),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    player: Player,
    position: Long,
    playing: Boolean,
    learning: Boolean,
    maxDelay: Long,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    onSetLearning: (Boolean) -> Unit,
    onSetMaxDelay: (Long) -> Unit,
    subtitleSource: SubtitleSource,
    embeddedTracks: List<EmbeddedTrack>,
    selectedTrack: EmbeddedTrack?,
    hasExternal: Boolean,
    onSelectNoSubtitle: () -> Unit,
    onSelectEmbedded: (EmbeddedTrack) -> Unit,
    onSelectExternal: () -> Unit
) {
    val duration = player.duration.coerceAtLeast(0L)
    Column(Modifier.fillMaxWidth().background(Color(0xFF101010)).padding(8.dp)) {
        Slider(
            value = if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { player.seekTo((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(formatMs(position) + " / " + formatMs(duration), color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
            }
        }
        if (showSettings) {
            Text("字幕来源", color = Color.White, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = subtitleSource == SubtitleSource.NONE,
                    onClick = onSelectNoSubtitle,
                    label = { Text("无") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                if (embeddedTracks.isNotEmpty()) {
                    FilterChip(
                        selected = subtitleSource == SubtitleSource.EMBEDDED,
                        onClick = { onSelectEmbedded(embeddedTracks.first()) },
                        label = { Text("内嵌字幕") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                if (hasExternal) {
                    FilterChip(
                        selected = subtitleSource == SubtitleSource.EXTERNAL,
                        onClick = onSelectExternal,
                        label = { Text("外部字幕") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            if (subtitleSource == SubtitleSource.EMBEDDED && embeddedTracks.size > 1) {
                Text(
                    "选择字幕轨",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    embeddedTracks.forEach { t: EmbeddedTrack ->
                        FilterChip(
                            selected = selectedTrack == t,
                            onClick = { onSelectEmbedded(t) },
                            label = { Text(t.displayName) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("学习模式", color = Color.White, fontSize = 14.sp)
                Switch(checked = learning, onCheckedChange = onSetLearning)
                Text(
                    if (learning) "中文延迟显示" else "中文立即显示",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("延迟上限", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                for (ms in listOf(3000L, 5000L)) {
                    FilterChip(
                        selected = maxDelay == ms,
                        onClick = { onSetMaxDelay(ms) },
                        label = { Text("${ms / 1000}s") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}
