package com.huqi.delayedsub.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Media3 ExoPlayer 封装。
 *
 * 关键决策：**禁用播放器内置字幕轨**（`C.TRACK_TYPE_TEXT`）。
 * 字幕由我们自己的链路解析并在 Compose 覆盖层渲染，
 * 这样才能做到"同一条字幕里中文比英文晚出现"。
 *
 * 复用成熟框架，不自己写播放器；ExoPlayer 原生支持 mp4 / mkv / HEVC 硬解。
 */
object Media3Player {

    fun create(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context.applicationContext)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                playWhenReady = true
            }
    }

    fun prepare(player: ExoPlayer, uri: Uri, startMs: Long = 0L) {
        val item = MediaItem.fromUri(uri)
        player.setMediaItem(item, startMs)
        player.prepare()
    }
}
