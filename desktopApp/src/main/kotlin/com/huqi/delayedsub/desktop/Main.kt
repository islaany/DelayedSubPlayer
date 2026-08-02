package com.huqi.delayedsub.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val model = remember { AppModel() }
    DisposableEffect(Unit) { onDispose { model.dispose() } }
    Window(onCloseRequest = ::exitApplication, title = "延迟字幕学习播放器 · 桌面版") {
        MaterialTheme {
            PlayerScreen(model)
        }
    }
}
