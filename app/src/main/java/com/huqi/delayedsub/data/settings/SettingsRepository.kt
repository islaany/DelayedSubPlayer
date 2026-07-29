package com.huqi.delayedsub.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "settings")

/**
 * 学习设置持久化（DataStore Preferences）：
 * - maxDelayMs：中文延迟上限（默认 3000ms，可切 5000ms）
 * - learningMode：是否启用学习模式（默认开启）
 */
class SettingsRepository(context: Context) {

    private val ds = context.applicationContext.dataStore

    val maxDelayMs: Flow<Long> = ds.data.map { it[MAX_DELAY] ?: 3000L }
    val learningMode: Flow<Boolean> = ds.data.map { it[LEARNING] ?: true }

    suspend fun setMaxDelayMs(ms: Long) = ds.edit { it[MAX_DELAY] = ms }
    suspend fun setLearningMode(on: Boolean) = ds.edit { it[LEARNING] = on }

    companion object {
        private val MAX_DELAY = longPreferencesKey("max_delay_ms")
        private val LEARNING = booleanPreferencesKey("learning_mode")
    }
}
