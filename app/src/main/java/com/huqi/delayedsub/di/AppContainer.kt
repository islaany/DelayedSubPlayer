package com.huqi.delayedsub.di

import android.app.Application
import com.huqi.delayedsub.data.database.AppDatabase
import com.huqi.delayedsub.data.database.VideoRepository
import com.huqi.delayedsub.data.settings.SettingsRepository
import com.huqi.delayedsub.data.subtitle.SubtitleRepository

/**
 * 手动依赖容器（MVVM 中没有引入 Hilt，保持轻量）。
 * 在 [com.huqi.delayedsub.DelayedSubApplication] 中创建，供各 ViewModel 取用。
 */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.build(app)
    val videoRepository: VideoRepository = VideoRepository(database.videoDao())
    val settingsRepository: SettingsRepository = SettingsRepository(app)
    val subtitleRepository: SubtitleRepository = SubtitleRepository()
}
