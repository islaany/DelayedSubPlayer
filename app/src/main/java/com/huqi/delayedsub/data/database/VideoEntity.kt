package com.huqi.delayedsub.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 最近学习的视频记录。
 */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUri: String,
    val subtitleUri: String? = null,
    val title: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val lastPositionMs: Long = 0L
)
