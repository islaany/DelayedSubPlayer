package com.huqi.delayedsub.desktop

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component

/**
 * 基于 VLCJ 的桌面视频播放封装。
 *
 * VLCJ 4.x 的 API 是分组的（media() / controls() / status() / events()），
 * 不是旧版直接在 MediaPlayer 上的扁平方法。
 *
 * 注意：VLCJ 依赖系统中的 libVLC（即需要安装 VLC 播放器）。构造 EmbeddedMediaPlayerComponent
 * 时会立即加载 libVLC，找不到会抛异常，由调用方捕获并提示用户安装 VLC。
 */
class DesktopPlayer(
    private val onTime: (Long) -> Unit,
    private val onDuration: (Long) -> Unit,
    private val onEnd: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val component: EmbeddedMediaPlayerComponent = EmbeddedMediaPlayerComponent()
    val view: Component get() = component

    private val listener = object : MediaPlayerEventAdapter() {
        override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) = onTime(newTime)
        override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) = onDuration(newLength)
        override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) {
            runCatching { onDuration(component.mediaPlayer().status().length()) }
        }
        override fun finished(mediaPlayer: MediaPlayer?) = onEnd()
        override fun error(mediaPlayer: MediaPlayer?) = onError("播放出错（可能是格式不支持或文件损坏）")
    }

    init {
        component.mediaPlayer().events().addMediaPlayerEventListener(listener)
    }

    /** 打开并立即播放（本地路径或 http/https 网络链接均可）。 */
    fun load(path: String) = component.mediaPlayer().media().play(path)

    /** 继续播放（从暂停恢复）。 */
    fun play() = component.mediaPlayer().controls().play()

    /** 暂停。 */
    fun pause() = component.mediaPlayer().controls().pause()

    /** 跳转到指定毫秒位置。 */
    fun seek(ms: Long) = component.mediaPlayer().controls().setTime(ms)

    fun dispose() = runCatching { component.mediaPlayer().release() }
}
